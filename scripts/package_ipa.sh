#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BINARY_PATH="${ROOT_DIR}/build/bin/iosArm64/releaseExecutable/SmashFest.kexe"
STAGE_DIR="${ROOT_DIR}/build/ipa"
APP_DIR="${STAGE_DIR}/Payload/SmashFest.app"
IPA_OUT="${ROOT_DIR}/SmashFest-unsigned.ipa"

if [[ ! -f "${BINARY_PATH}" ]]; then
  echo "ERROR: Expected compiled Kotlin/Native iOS binary at ${BINARY_PATH}" >&2
  exit 1
fi

rm -rf "${STAGE_DIR}" "${IPA_OUT}"
mkdir -p "${APP_DIR}"

cp "${BINARY_PATH}" "${APP_DIR}/SmashFest"
chmod 755 "${APP_DIR}/SmashFest"

cp "${ROOT_DIR}/iosResources/Info.plist" "${APP_DIR}/Info.plist"
printf "APPL????" > "${APP_DIR}/PkgInfo"

if command -v plutil >/dev/null 2>&1; then
  plutil -lint "${APP_DIR}/Info.plist"
fi

python3 "${ROOT_DIR}/scripts/generate_icons.py" "${APP_DIR}"

# Ad-hoc Mach-O code signature so AltStore / Sideloadly / TrollStore / ESign accept the bundle cleanly
if command -v codesign >/dev/null 2>&1; then
  codesign --force --sign - --timestamp=none "${APP_DIR}"
fi

(
  cd "${STAGE_DIR}"
  zip -qry "${IPA_OUT}" Payload
)

echo "=== IPA Verification ==="
ls -lh "${IPA_OUT}"
file "${APP_DIR}/SmashFest"
unzip -l "${IPA_OUT}" | grep -E "SmashFest|Info.plist|AppIcon"
echo "Successfully packaged ${IPA_OUT}"
