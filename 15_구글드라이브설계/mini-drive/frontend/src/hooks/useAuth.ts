import { useMutation } from "@tanstack/react-query";
import { useSyncExternalStore } from "react";
import { authApi } from "@/api/auth";
import { tokenStore } from "@/api/tokenStore";
import type { AuthUser, LoginRequest, SignupRequest } from "@/types/api";

interface AuthState {
  user: AuthUser | null;
  isAuthenticated: boolean;
}

export function useAuth(): AuthState {
  return useSyncExternalStore(
    (cb) => tokenStore.subscribe(cb),
    () => snapshot(),
    () => snapshot()
  );
}

let cached: AuthState | null = null;
let cachedAccess: string | null = null;
let cachedUserRaw: string | null = null;

function snapshot(): AuthState {
  const access = tokenStore.getAccess();
  const userRaw = JSON.stringify(tokenStore.getUser());
  if (cached && access === cachedAccess && userRaw === cachedUserRaw) {
    return cached;
  }
  cachedAccess = access;
  cachedUserRaw = userRaw;
  cached = {
    user: tokenStore.getUser(),
    isAuthenticated: !!access,
  };
  return cached;
}

export function useSignup() {
  return useMutation({
    mutationFn: (req: SignupRequest) => authApi.signup(req),
  });
}

export function useLogin() {
  return useMutation({
    mutationFn: (req: LoginRequest) => authApi.login(req),
  });
}

export function useLogout() {
  return useMutation({
    mutationFn: () => authApi.logout(),
  });
}
