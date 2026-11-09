/** Chuẩn hoá id category (API number / string). */
export function catId(value) {
  return value == null || value === '' ? null : String(value)
}

/** Map parentId → children[] từ list phẳng API. */
export function buildChildrenMap(categories) {
  const map = new Map()
  for (const c of categories) {
    const pid = catId(c.parentId)
    if (!map.has(pid)) map.set(pid, [])
    map.get(pid).push({
      id: catId(c.id),
      name: c.name,
      parentId: pid,
    })
  }
  return map
}

/** Danh mục gốc (không có cha). */
export function getRootCategories(categories) {
  return categories
    .filter((c) => c.parentId == null)
    .map((c) => ({ id: catId(c.id), name: c.name, parentId: null }))
}

/** id danh mục + mọi con cháu (để lọc sản phẩm theo nhánh). */
export function getCategoryAndDescendantIds(categories, categoryId) {
  const root = catId(categoryId)
  if (!root || root === 'all') return null

  const childrenByParent = buildChildrenMap(categories)
  const ids = new Set([root])
  const queue = [root]
  while (queue.length) {
    const cur = queue.shift()
    for (const child of childrenByParent.get(cur) || []) {
      if (!ids.has(child.id)) {
        ids.add(child.id)
        queue.push(child.id)
      }
    }
  }
  return ids
}

export function findCategory(categories, categoryId) {
  const id = catId(categoryId)
  if (!id || id === 'all') return null
  return categories.find((c) => catId(c.id) === id) || null
}

/** Chuỗi tổ tiên từ gốc → category (không gồm chính nó). */
export function getAncestorIds(categories, categoryId) {
  const byId = new Map(categories.map((c) => [catId(c.id), c]))
  const ancestors = []
  let current = byId.get(catId(categoryId))
  while (current?.parentId != null) {
    const parent = byId.get(catId(current.parentId))
    if (!parent) break
    ancestors.unshift(catId(parent.id))
    current = parent
  }
  return ancestors
}
