import { tokenStore } from "@/api/tokenStore";

// 계약 v1.6: 인증이 필요한 다운로드 경로(/api/files/{id}/download,
// /api/files/{id}/versions/{version}/download)는 게이트웨이 경유 스트리밍이다.
// 백엔드는 인가 후 바디 없는 200 + X-Accel-Redirect + Content-Disposition: attachment 헤더만
// 반환하고, nginx 가 그 X-Accel-Redirect 를 소비해 MinIO 에서 직접 바이트를 스트리밍한다.
// (브라우저 입장에선 결과적으로 attachment 스트림을 수신한다.) presigned URL(JSON) 은 더 이상 반환하지 않는다.
// 단순 <a href> 로는 Authorization 헤더가 실리지 않으므로 fetch 로 blob 을 받아 저장한다.
export async function downloadAuthenticated(path: string): Promise<void> {
  const token = tokenStore.getAccess();
  const res = await fetch(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    throw new Error(`다운로드 실패 (${res.status})`);
  }

  const filename = parseFilename(res.headers.get("content-disposition"));
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename ?? "";
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

// Content-Disposition 에서 파일명 추출. 계약은 RFC5987(filename*=UTF-8''...) 인코딩 사용.
function parseFilename(disposition: string | null): string | undefined {
  if (!disposition) return undefined;
  const star = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (star) {
    try {
      return decodeURIComponent(star[1].trim());
    } catch {
      return star[1].trim();
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain ? plain[1].trim() : undefined;
}
