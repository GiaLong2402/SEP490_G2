/**
 * One-off: làm đẹp tồn đầu kỳ OPEN — TP theo listPrice/1.3 (sàn theo quy cách),
 * NVL theo giá lô còn tồn; đồng bộ lot.unit_cost TP.
 * Chạy: node scripts/fix-opening-wac.mjs  (đọc ../.env)
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import pg from 'pg';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const envPath = path.join(__dirname, '..', '.env');
const env = Object.fromEntries(
  fs
    .readFileSync(envPath, 'utf8')
    .split(/\r?\n/)
    .filter((l) => l && !l.startsWith('#') && l.includes('='))
    .map((l) => {
      const i = l.indexOf('=');
      return [l.slice(0, i).trim(), l.slice(i + 1).trim()];
    }),
);

const jdbc = env.DB_URL || '';
const m = jdbc.match(/jdbc:postgresql:\/\/([^/?]+)\/([^?]+)/);
if (!m) throw new Error('Cannot parse DB_URL');
const connectionString = `postgresql://${env.DB_USERNAME}:${env.DB_PASSWORD}@${m[1]}/${m[2]}?sslmode=require`;

const client = new pg.Client({ connectionString, ssl: { rejectUnauthorized: false } });
await client.connect();

try {
  await client.query('begin');

  const period = (
    await client.query(
      `select id, period_start::text
       from hbsisms.cost_period
       where status = 'OPEN'
       order by period_start desc
       limit 1`,
    )
  ).rows[0];
  if (!period) throw new Error('No OPEN cost period');
  console.log('OPEN period', period.id, period.period_start);

  // Target unit cost for FG: list_price / 1.3, floor by pack (1kg≥120000, 0.5kg≥60000), else 135000/67500
  const updFg = await client.query(
    `
    with targets as (
      select
        b.id as balance_id,
        b.item_id as variant_id,
        b.opening_qty,
        v.sku,
        v.unit_weight_g,
        v.list_price,
        greatest(
          case
            when coalesce(v.unit_weight_g, 0) >= 900 then 120000
            when coalesce(v.unit_weight_g, 0) between 400 and 600 then 60000
            when v.pack_label ilike '%1%kg%' then 120000
            when v.pack_label ilike '%0,5%' or v.pack_label ilike '%0.5%' then 60000
            else 100000
          end,
          case
            when v.list_price is not null and v.list_price > 0
              then round(v.list_price / 1.3)
            else 0
          end
        )::numeric(14,2) as unit_cost
      from hbsisms.cost_item_balance b
      join hbsisms.product_variant v on v.id = b.item_id
      where b.period_id = $1
        and b.item_type = 'FINISHED'
        and b.opening_qty > 0
    ),
    patched as (
      update hbsisms.cost_item_balance b
      set
        opening_value = round(t.opening_qty * t.unit_cost, 2),
        closing_qty = t.opening_qty
          + coalesce(b.inbound_qty, 0)
          - coalesce(b.outbound_qty, 0),
        closing_value = round(
          (t.opening_qty * t.unit_cost)
            + coalesce(b.inbound_value, 0)
            - coalesce(b.outbound_value, 0),
          2
        ),
        avg_unit_cost = round(
          (
            (t.opening_qty * t.unit_cost) + coalesce(b.inbound_value, 0)
          ) / nullif(t.opening_qty + coalesce(b.inbound_qty, 0), 0),
          2
        )
      from targets t
      where b.id = t.balance_id
      returning b.id, t.sku, t.opening_qty, t.unit_cost, b.opening_value, b.avg_unit_cost
    )
    select * from patched order by sku
    `,
    [period.id],
  );
  console.log('FINISHED balances updated:', updFg.rowCount);
  for (const r of updFg.rows) {
    console.log(
      `  ${r.sku}: qty=${r.opening_qty} unit=${r.unit_cost} opening=${r.opening_value} avg=${r.avg_unit_cost}`,
    );
  }

  const updLots = await client.query(
    `
    with targets as (
      select
        v.id as variant_id,
        greatest(
          case
            when coalesce(v.unit_weight_g, 0) >= 900 then 120000
            when coalesce(v.unit_weight_g, 0) between 400 and 600 then 60000
            when v.pack_label ilike '%1%kg%' then 120000
            when v.pack_label ilike '%0,5%' or v.pack_label ilike '%0.5%' then 60000
            else 100000
          end,
          case
            when v.list_price is not null and v.list_price > 0
              then round(v.list_price / 1.3)
            else 0
          end
        )::numeric(14,2) as unit_cost
      from hbsisms.product_variant v
    )
    update hbsisms.lot l
    set unit_cost = t.unit_cost
    from targets t
    where l.variant_id = t.variant_id
      and (l.unit_cost is null or l.unit_cost < t.unit_cost * 0.5 or l.unit_cost = 0)
    returning l.id, l.lot_code, l.unit_cost
    `,
  );
  console.log('Lots unit_cost patched:', updLots.rowCount);

  // NVL: nếu tồn đầu > 0 nhưng giá trị ~0 → lấy avg từ lô còn tồn
  const updRaw = await client.query(
    `
    with lot_avg as (
      select
        rm.id as raw_id,
        case
          when sum(rl.qty_remaining) > 0
            then round(sum(rl.qty_remaining * coalesce(rl.unit_price, 0)) / sum(rl.qty_remaining), 2)
          else null
        end as unit_cost
      from hbsisms.raw_material rm
      join hbsisms.raw_material_lot rl on rl.raw_material_id = rm.id
      where rl.qty_remaining > 0
      group by rm.id
    ),
    targets as (
      select b.id as balance_id, b.opening_qty, la.unit_cost, rm.code
      from hbsisms.cost_item_balance b
      join hbsisms.raw_material rm on rm.id = b.item_id
      join lot_avg la on la.raw_id = b.item_id
      where b.period_id = $1
        and b.item_type = 'RAW_MATERIAL'
        and b.opening_qty > 0
        and coalesce(b.opening_value, 0) = 0
        and la.unit_cost is not null
        and la.unit_cost > 0
    ),
    patched as (
      update hbsisms.cost_item_balance b
      set
        opening_value = round(t.opening_qty * t.unit_cost, 2),
        closing_qty = t.opening_qty
          + coalesce(b.inbound_qty, 0)
          - coalesce(b.outbound_qty, 0),
        closing_value = round(
          (t.opening_qty * t.unit_cost)
            + coalesce(b.inbound_value, 0)
            - coalesce(b.outbound_value, 0),
          2
        ),
        avg_unit_cost = t.unit_cost
      from targets t
      where b.id = t.balance_id
      returning t.code, t.opening_qty, t.unit_cost, b.opening_value
    )
    select * from patched order by code
    `,
    [period.id],
  );
  console.log('RAW_MATERIAL zero-value openings fixed:', updRaw.rowCount);
  for (const r of updRaw.rows) {
    console.log(`  ${r.code}: qty=${r.opening_qty} unit=${r.unit_cost} opening=${r.opening_value}`);
  }

  await client.query('commit');
  console.log('Done.');
} catch (e) {
  await client.query('rollback');
  console.error(e);
  process.exitCode = 1;
} finally {
  await client.end();
}
