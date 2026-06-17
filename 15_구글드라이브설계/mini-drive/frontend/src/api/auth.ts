import { api } from "./client";
import { tokenStore } from "./tokenStore";
import type {
  LoginRequest,
  LoginResponse,
  SignupRequest,
  SignupResponse,
} from "@/types/api";

export const authApi = {
  signup: (req: SignupRequest) =>
    api.post<SignupResponse>("/auth/signup", req, { auth: false }),

  login: async (req: LoginRequest): Promise<LoginResponse> => {
    const res = await api.post<LoginResponse>("/auth/login", req, {
      auth: false,
    });
    tokenStore.setSession(res.accessToken, res.refreshToken, res.user);
    return res;
  },

  logout: async (): Promise<void> => {
    const refreshToken = tokenStore.getRefresh();
    try {
      if (refreshToken) {
        await api.post<void>("/auth/logout", { refreshToken });
      }
    } finally {
      tokenStore.clear();
    }
  },
};
