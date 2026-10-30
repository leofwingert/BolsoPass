import { fromBase64Url, toBase64Url } from '@/domain/ticketQr';

/** ECDSA P-256 + SHA-256 via WebCrypto (mesmo algoritmo do ticketing-service, RF-13). */
const ALGO: EcKeyImportParams = { name: 'ECDSA', namedCurve: 'P-256' };
const SIGN: EcdsaParams = { name: 'ECDSA', hash: 'SHA-256' };

export async function generateKeyPair(): Promise<CryptoKeyPair> {
  return crypto.subtle.generateKey(ALGO, true, ['sign', 'verify']);
}

export async function sign(privateKey: CryptoKey, data: string): Promise<string> {
  const sig = await crypto.subtle.sign(SIGN, privateKey, new TextEncoder().encode(data));
  return toBase64Url(new Uint8Array(sig));
}

export async function exportPublicKey(key: CryptoKey): Promise<JsonWebKey> {
  return crypto.subtle.exportKey('jwk', key);
}

export async function importPublicKey(jwk: JsonWebKey): Promise<CryptoKey> {
  return crypto.subtle.importKey('jwk', jwk, ALGO, true, ['verify']);
}

export async function verify(publicKey: CryptoKey, data: string, signature: string): Promise<boolean> {
  try {
    return await crypto.subtle.verify(
      SIGN,
      publicKey,
      fromBase64Url(signature),
      new TextEncoder().encode(data),
    );
  } catch {
    return false;
  }
}
