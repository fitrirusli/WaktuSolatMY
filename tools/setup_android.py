"""Prepare a project-local Android SDK and Gradle; no machine-wide changes."""
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import hashlib
import os
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
SDK = TOOLS / 'android-sdk'
JAVA = Path(os.environ.get('JAVA_HOME', r'C:\Program Files\JetBrains\PyCharm Community Edition 2024.3.5\jbr'))


def download(url, name, checksum_url=None):
    dest = TOOLS / name
    TOOLS.mkdir(exist_ok=True)
    if not dest.exists():
        print(f'Downloading {name}', flush=True)
        with urllib.request.urlopen(url, timeout=120) as source, dest.with_suffix('.partial').open('wb') as target:
            while block := source.read(1024 * 1024):
                target.write(block)
        dest.with_suffix('.partial').replace(dest)
    if checksum_url:
        expected = urllib.request.urlopen(checksum_url, timeout=30).read().decode().strip().split()[0]
        actual = hashlib.file_digest(dest.open('rb'), 'sha256').hexdigest()
        if actual != expected:
            raise RuntimeError(f'Checksum mismatch: {name}')
    print(f'Ready: {name}', flush=True)
    return dest


def main():
    java = JAVA
    if not (java / 'bin' / 'jlink.exe').exists():
        archive = download('https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_windows_hotspot_17.0.21_9.zip', 'temurin-jdk17.zip')
        if hashlib.file_digest(archive.open('rb'), 'sha256').hexdigest() != '98518ecf554465057ee5509462631deeee374bc368c12a411d225cd29381f84b':
            raise RuntimeError('JDK checksum mismatch')
        with zipfile.ZipFile(archive) as bundle:
            folder = bundle.namelist()[0].split('/')[0]
            if not (TOOLS / folder / 'bin' / 'jlink.exe').exists():
                bundle.extractall(TOOLS)
        java = TOOLS / folder
    (TOOLS / 'java-home.txt').write_text(str(java), encoding='utf-8')
    with ThreadPoolExecutor(max_workers=2) as pool:
        gradle_job = pool.submit(download, 'https://downloads.gradle.org/distributions/gradle-8.13-bin.zip', 'gradle-8.13-bin.zip', 'https://downloads.gradle.org/distributions/gradle-8.13-bin.zip.sha256')
        sdk_job = pool.submit(download, 'https://dl.google.com/android/repository/commandlinetools-win-13114758_latest.zip', 'android-commandlinetools.zip')
        gradle_zip, sdk_zip = gradle_job.result(), sdk_job.result()
    if not (TOOLS / 'gradle-8.13' / 'bin' / 'gradle.bat').exists():
        with zipfile.ZipFile(gradle_zip) as bundle:
            bundle.extractall(TOOLS)
    manager = SDK / 'cmdline-tools' / 'latest' / 'bin' / 'sdkmanager.bat'
    if not manager.exists():
        with zipfile.ZipFile(sdk_zip) as bundle:
            for entry in bundle.infolist():
                relative = Path(entry.filename).relative_to('cmdline-tools')
                target = SDK / 'cmdline-tools' / 'latest' / relative
                if entry.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(bundle.read(entry))
    env = dict(os.environ, JAVA_HOME=str(java), ANDROID_HOME=str(SDK))
    command = [str(manager), f'--sdk_root={SDK}', 'platforms;android-36', 'build-tools;35.0.0', 'platform-tools']
    print('Installing Android SDK packages (project-local).', flush=True)
    subprocess.run(command, input='y\n' * 100, text=True, env=env, check=True)
    (ROOT / 'local.properties').write_text('sdk.dir=' + SDK.as_posix().replace(':', '\\:') + '\n', encoding='utf-8')
    print('Android build tools ready.', flush=True)


if __name__ == '__main__':
    main()
