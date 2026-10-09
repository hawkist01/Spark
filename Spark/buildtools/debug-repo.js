const REPOS = ['https://dl.google.com/android/repository/repository2-1.xml',
  'https://dl.google.com/android/repository/repository2-2.xml',
  'https://dl.google.com/android/repository/repository2-3.xml'];
const WANT = ['platforms;android-35', 'build-tools;35.0.0', 'platforms;android-35'];
async function getXml(u){const r=await fetch(u,{redirect:'follow'});if(!r.ok)return null;return await r.text();}
(async()=>{
  for(const r of REPOS){
    const t=await getXml(r);
    if(!t){console.log(r+': no');continue;}
    console.log('==== '+r+' len='+t.length+' remotePackage='+(t.indexOf('remotePackage')>=0));
    for(const p of ['platforms;android-35','build-tools;35.0.0']){
      const m=t.match(new RegExp('<remotePackage\\s+path="'+p.replace(/;/g,';')+'"[\\s\\S]*?<\\/remotePackage>'));
      if(!m){console.log('  ['+p+'] NOT FOUND');continue;}
      const block=m[0];
      console.log('  ['+p+'] BLOCK LEN='+block.length);
      const archRe=/<archive>([\s\S]*?)<\/archive>/g;let a;let n=0;
      while((a=archRe.exec(block))){
        n++;
        const ab=a[1];
        const host=(ab.match(/<host-os>([^<]+)<\/host-os>/)||[])[1]||'?';
        const url=(ab.match(/<url>([^<]+)<\/url>/)||[])[1]||'?';
        console.log('     arch#'+n+' host='+host+' url='+url);
      }
    }
  }
})();
