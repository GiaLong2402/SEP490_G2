import { StrictMode } from 'react';
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router-dom';
import { AuthProvider } from '@/features/auth';
import { ApiError, configureApi, createHttpTransport } from '@/shared/api/client';
import { API_BASE_URL, isMockMode } from '@/shared/api/config';
import { ToastProvider } from '@/shared/ui';
import { router } from './routes';
import './index.css';

// Nghiệp vụ liên thông (đơn → kho, công nợ, duyệt) nên sau mỗi thao tác ghi làm mới toàn bộ dữ liệu đang hiển thị.
const queryClient: QueryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 15_000,
      refetchOnWindowFocus: false,
      retry: (count, error) => !(error instanceof ApiError && error.status < 500) && count < 2,
    },
  },
  mutationCache: new MutationCache({ onSuccess: () => void queryClient.invalidateQueries() }),
});

async function bootstrap() {
  if (isMockMode) {
    const { mockTransport } = await import('./mockServer');
    configureApi(mockTransport);
  } else {
    configureApi(createHttpTransport(API_BASE_URL));
  }
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <AuthProvider>
            <RouterProvider router={router} />
          </AuthProvider>
        </ToastProvider>
      </QueryClientProvider>
    </StrictMode>,
  );
}

void bootstrap();
