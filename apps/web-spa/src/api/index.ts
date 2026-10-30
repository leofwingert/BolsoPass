import { env } from '@/config/env';
import { mockApi, mockRealtime } from '@/mocks/mockApi';
import type { BolsoPassApi } from './contract';
import { httpApi } from './httpApi';
import { WebSocketRealtime, type RealtimeClient } from './realtime';
import { getAccessToken } from './session';

/** Ponto único de acesso à API. Troque VITE_API_MODE para "http" quando o backend subir. */
export const api: BolsoPassApi = env.apiMode === 'http' ? httpApi : mockApi;

export const realtime: RealtimeClient =
  env.apiMode === 'http' ? new WebSocketRealtime(env.wsUrl, getAccessToken) : mockRealtime;

export const isMockMode = env.apiMode !== 'http';
