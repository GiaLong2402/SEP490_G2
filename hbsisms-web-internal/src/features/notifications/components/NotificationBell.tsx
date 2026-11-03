import { useEffect, useRef, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { Bell } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { dateTime } from '@/shared/lib/format';
import { notificationsApi } from '../api';

export function NotificationBell() {
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const { data } = useQuery({ queryKey: ['notifications'], queryFn: notificationsApi.list, refetchInterval: 60_000 });
  const markRead = useMutation({ mutationFn: notificationsApi.markRead });
  const markAll = useMutation({ mutationFn: notificationsApi.markAllRead });
  const unread = data?.filter((n) => !n.isRead).length ?? 0;

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false);
    };
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', close);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', close);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  return (
    <div ref={rootRef} className="relative">
      <button type="button" aria-label={unread ? `Thông báo, ${unread} chưa đọc` : 'Thông báo'} aria-expanded={open} onClick={() => setOpen(!open)} className="relative flex h-9 w-9 items-center justify-center rounded-md text-ink-muted hover:bg-rowalt hover:text-ink">
        <Bell size={18} aria-hidden />
        {unread > 0 && <span className="absolute right-0.5 top-0.5 flex h-4 min-w-4 items-center justify-center rounded-full bg-coral px-1 text-[10px] font-semibold text-white">{unread}</span>}
      </button>
      {open && (
        <div className="absolute right-0 z-30 mt-1 w-[340px] max-w-[calc(100vw-32px)] rounded-md border border-line bg-card shadow-pop">
          <div className="flex items-center justify-between border-b border-line px-3 py-2">
            <h2 className="text-h2">Thông báo</h2>
            {unread > 0 && <button type="button" className="text-small text-primary hover:underline" onClick={() => markAll.mutate()}>Đánh dấu đã đọc</button>}
          </div>
          <ul className="max-h-[360px] overflow-y-auto">
            {data?.length === 0 && <li className="px-3 py-8 text-center text-ink-muted">Chưa có thông báo</li>}
            {data?.map((n) => (
              <li key={n.id}>
                <button
                  type="button"
                  className={`block w-full border-b border-line px-3 py-2.5 text-left last:border-b-0 hover:bg-rowalt ${n.isRead ? '' : 'bg-primary-soft'}`}
                  onClick={() => {
                    if (!n.isRead) markRead.mutate(n.id);
                    setOpen(false);
                    if (n.link) navigate(n.link);
                  }}
                >
                  <div className={n.isRead ? '' : 'font-semibold'}>{n.title}</div>
                  {n.body && <div className="text-small text-ink-muted">{n.body}</div>}
                  <div className="mt-0.5 text-micro font-normal text-ink-faint">{dateTime(n.createdAt)}</div>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
