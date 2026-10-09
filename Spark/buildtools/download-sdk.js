const fs = require('fs');
const path = require('path');

const REPOS = [
  'https://dl.google.com/android/repository/repository2-3.xml',
  'https://dl.google.com/android/repository/repository2-2.xml',
  'https://dl.google.com/android/repository/repository2-1.xml',
];
const WANT = ['platforms;android-36', 'build-tools;36.1.0'];
const BASE = 'https://dl.google.com/android/repository/';
const outDir = process.argv[2];
fs.mkdirSync(outDir, { recursive: true });

async function getXml(url) {
  const r = await fetch(url, { redirect: 'follow' });
  if (!r.ok) return null;
  return await r.text();
}

function findWindowsFile(packagePath, xml) {
  const blockRe = new RegExp('<remotePackage\\s+path="' + packagePath + '"[\\s\\S]*?<\\/remotePackage>', 'g');
  let bm;
  const windows = [];
  while ((bm = blockRe.exec(xml))) {
    const archRe = /<archive>([\s\S]*?)<\/archive>/g;
    let arch;
    while ((arch = archRe.exec(bm[0]))) {
      const ab = arch[1];
      const hostm = ab.match(/<host-os>([^<]+)<\/host-os>/);
      const urlm = ab.match(/<url>([^<]+)<\/url>/);
      if (!urlm) continue;
      const host = hostm ? hostm[1] : '';
      // platform zips are host-independent (no host-os tag); build-tools need windows
      if (packagePath.indexOf('platforms;') === 0) {
        if (!hostm || host.toLowerCase().indexOf('windows') >= 0) windows.push(urlm[1]);
      } else {
        if (host.toLowerCase().indexOf('windows') >= 0) windows.push(urlm[1]);
      }
    }
  }
  if (windows.length === 0) return null;
  if (packagePath.indexOf('platforms;') === 0) {
    const std = windows.find(u => /^platform-\d+_r\d+\.zip/i.test(u));
    if (std) return std;
  }
  return windows[0];
}

(async () => {
  let xml = null, src = null;
  for (const r of REPOS) {
    const t = await getXml(r);
    if (t && /remotePackage/.test(t)) {
      const files = {};
      let ok = true;
      for (const p of WANT) { const f = findWindowsFile(p, t); if (!f) { ok = false; break; } files[p] = f; }
      if (ok) { xml = t; src = r; }
      else { process.stdout.write('repo ' + r + ' -> not all windows files present\n'); continue; }
      break;
    } else {
      process.stdout.write('repo ' + r + ' -> unreachable/no packages\n');
    }
  }
  if (!xml) { process.stdout.write('NOXML FOUND\n'); return; }
  process.stdout.write('using ' + src + '\n');
  for (const p of WANT) {
    const name = findWindowsFile(p, xml);
    process.stdout.write('PACKAGE ' + p + ' => ' + name + '\n');
    const fp = path.join(outDir, name);
    try {
      const r = await fetch(BASE + name, { redirect: 'follow' });
      if (!r.ok) { process.stdout.write('   download HTTP ' + r.status + '\n'); continue; }
      const buf = Buffer.from(await r.arrayBuffer());
      fs.writeFileSync(fp, buf);
      process.stdout.write('   SAVED ' + name + ' bytes=' + buf.length + ' declared=' + r.headers.get('content-length') + '\n');
    } catch (e) { process.stdout.write('   ERR ' + e.message + '\n'); }
  }
  process.stdout.write('SDKDONE\n');
})();
