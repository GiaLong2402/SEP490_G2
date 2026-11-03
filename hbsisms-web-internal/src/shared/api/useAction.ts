import { useMutation } from '@tanstack/react-query';
import { useToast } from '@/shared/ui/Toast';

/** Mutation kèm toast. Dữ liệu được làm mới toàn cục ở MutationCache (app/providers.tsx). */
export function useAction<V = void, R = unknown>(fn: (vars: V) => Promise<R>, options: { success?: string | ((result: R) => string); onDone?: (result: R) => void } = {}) {
  const toast = useToast();
  return useMutation<R, Error, V>({
    mutationFn: fn,
    onSuccess: (result) => {
      if (options.success) toast.success(typeof options.success === 'function' ? options.success(result) : options.success);
      options.onDone?.(result);
    },
    onError: (error) => toast.error(error),
  });
}
