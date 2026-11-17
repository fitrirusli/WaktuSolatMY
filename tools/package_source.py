"""Package source only; excludes local tools, caches, keys and build outputs."""
from pathlib import Path
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parents[1]
DIST = ROOT / 'dist'
INCLUDE_DIRS = ['app/src', 'gradle', 'tools']
INCLUDE_FILES = ['.gitignore', 'README.md', 'SECURITY.md', 'COPYRIGHT', 'build.ps1', 'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle', 'app/proguard-rules.pro', 'signing/fitrirusli-release.pem', 'signing/rotation.lineage']

DIST.mkdir(exist_ok=True)
metadata = json.loads((ROOT / 'app/build/outputs/apk/release/output-metadata.json').read_text(encoding='utf-8'))
version = metadata['elements'][0]['versionName']
apk_name = f'WaktuSolatMY-v{version}-release.apk'
source_name = f'WaktuSolatMY-v{version}-source.zip'
with zipfile.ZipFile(DIST / source_name, 'w', zipfile.ZIP_DEFLATED) as bundle:
    for directory in INCLUDE_DIRS:
        for path in sorted((ROOT / directory).rglob('*')):
            if path.is_file() and '__pycache__' not in path.parts:
                bundle.write(path, 'WaktuSolatMY/' + path.relative_to(ROOT).as_posix())
    for name in INCLUDE_FILES:
        bundle.write(ROOT / name, 'WaktuSolatMY/' + name)
lines = []
with zipfile.ZipFile(DIST / source_name) as bundle:
    if any(name.lower().endswith(('.p12', '.pfx', '.jks', '.keystore', '.dpapi')) for name in bundle.namelist()):
        raise RuntimeError('Private signing material must not be included in the source archive.')
for name in [apk_name, source_name, f'WaktuSolatMY-v{version}-release.security.json']:
    path = DIST / name
    with path.open('rb') as stream:
        digest = hashlib.file_digest(stream, 'sha256').hexdigest()
    lines.append(f'{digest}  {name}')
    print(f'{name}: {path.stat().st_size:,} bytes')
(DIST / 'SHA256SUMS.txt').write_text('\n'.join(lines) + '\n', encoding='utf-8')
