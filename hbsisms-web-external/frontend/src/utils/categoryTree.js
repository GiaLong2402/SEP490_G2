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

/** True nếu là danh mục cha (không có parent). */
export function isRootCategory(category) {
  return category?.parentId == null || category?.parentId === ''
}

function sortCategories(list) {
  return list.slice().sort(
    (a, b) =>
      (a.sortOrder ?? 0) - (b.sortOrder ?? 0) ||
      String(a.name || '').localeCompare(String(b.name || ''), 'vi'),
  )
}

/** Con trực tiếp của một danh mục. */
export function getDirectChildren(categories, parentId) {
  const pid = catId(parentId)
  return sortCategories(
    (categories || []).filter((c) => catId(c.parentId) === pid && c.isActive !== false),
  ).map((c) => ({
    id: catId(c.id),
    name: c.name,
    parentId: pid,
    imageUrl: c.imageUrl ?? null,
    sortOrder: c.sortOrder ?? 0,
  }))
}

/** Danh mục gốc (không có cha), ưu tiên đang dùng, sắp theo sortOrder. */
export function getRootCategories(categories) {
  return sortCategories(
    (categories || []).filter((c) => isRootCategory(c) && c.isActive !== false),
  ).map((c) => ({
    id: catId(c.id),
    name: c.name,
    parentId: null,
    imageUrl: c.imageUrl ?? null,
    sortOrder: c.sortOrder ?? 0,
  }))
}

/** Gốc kèm children — dùng cho menu Danh mục sản phẩm. */
export function getRootCategoriesWithChildren(categories) {
  return getRootCategories(categories).map((root) => ({
    ...root,
    children: getDirectChildren(categories, root.id),
  }))
}

/**
 * Gốc có ít nhất 1 con — dùng tab Home (Các loại chả/cá/mực, Hải sản một nắng…),
 * bỏ các danh mục cha “phẳng” không có nhánh con.
 */
export function getBranchRootCategories(categories) {
  return getRootCategoriesWithChildren(categories).filter((root) => root.children.length > 0)
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
