import { mock } from '@/shared/api/mockServer';
import { db } from '@/shared/mock/db';

mock('GET', '/notifications', ({ userId }) => db.notifications.filter((n) => n.userId === userId).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 30));

mock('POST', '/notifications/read-all', ({ userId }) => {
  db.notifications.filter((n) => n.userId === userId).forEach((n) => (n.isRead = true));
});

mock('POST', '/notifications/:id/read', ({ params, userId }) => {
  const row = db.notifications.find((n) => n.id === Number(params.id) && n.userId === userId);
  if (row) row.isRead = true;
});
