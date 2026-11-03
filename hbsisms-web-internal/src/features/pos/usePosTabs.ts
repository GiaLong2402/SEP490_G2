import { useCallback, useEffect, useState } from 'react';
import type { ID } from '@/shared/domain/entities';
import type { PosTab } from './types';

export const MAX_TABS = 12;

const newId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;

function createTab(number: number): PosTab {
  return { id: newId(), number, customerId: null, lines: [], discount: '', paymentMethod: 'cash', tendered: '', reference: '', note: '' };
}

/** Số nhỏ nhất chưa dùng, để tên tab không tăng mãi trong ca bán. */
function nextNumber(tabs: PosTab[]) {
  let number = 1;
  while (tabs.some((t) => t.number === number)) number += 1;
  return number;
}

interface PosState {
  tabs: PosTab[];
  activeId: string;
}

function load(key: string): PosState {
  try {
    const saved = JSON.parse(localStorage.getItem(key) ?? 'null') as PosState | null;
    if (saved?.tabs?.length) return { tabs: saved.tabs, activeId: saved.tabs.some((t) => t.id === saved.activeId) ? saved.activeId : saved.tabs[0].id };
  } catch {
    // Dữ liệu nháp hỏng hoặc storage bị chặn: bắt đầu bằng một đơn trống.
  }
  const first = createTab(1);
  return { tabs: [first], activeId: first.id };
}

/** Các đơn đang mở lưu theo từng nhân viên, tải lại trang không mất. */
export function usePosTabs(userId: ID) {
  const storageKey = `hbsisms.pos.tabs.${userId}`;
  const [state, setState] = useState<PosState>(() => load(storageKey));

  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(state));
    } catch {
      // Không lưu được nháp thì vẫn bán được trong phiên hiện tại.
    }
  }, [state, storageKey]);

  const addTab = useCallback(() => {
    setState((s) => {
      if (s.tabs.length >= MAX_TABS) return s;
      const tab = createTab(nextNumber(s.tabs));
      return { tabs: [...s.tabs, tab], activeId: tab.id };
    });
  }, []);

  const selectTab = useCallback((id: string) => setState((s) => (s.tabs.some((t) => t.id === id) ? { ...s, activeId: id } : s)), []);

  const closeTab = useCallback((id: string) => {
    setState((s) => {
      const index = s.tabs.findIndex((t) => t.id === id);
      const rest = s.tabs.filter((t) => t.id !== id);
      if (rest.length === 0) {
        const tab = createTab(1);
        return { tabs: [tab], activeId: tab.id };
      }
      return { tabs: rest, activeId: s.activeId === id ? rest[Math.max(0, index - 1)].id : s.activeId };
    });
  }, []);

  const updateTab = useCallback((id: string, patch: Partial<PosTab>) => {
    setState((s) => ({ ...s, tabs: s.tabs.map((t) => (t.id === id ? { ...t, ...patch } : t)) }));
  }, []);

  const active = state.tabs.find((t) => t.id === state.activeId) ?? state.tabs[0];
  return { tabs: state.tabs, active, addTab, selectTab, closeTab, updateTab };
}
