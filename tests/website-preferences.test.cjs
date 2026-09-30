const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const path=require('node:path');
const directory=process.argv[2];
let passed=0;
for(const [language,expectedLanguage] of [['en','en'],['zh-Hans','zh-cn'],['ms','ms']]) {
  for(const [mode,expectedMode] of [['light','off'],['dark','on'],['system','auto']]) {
    const script=fs.readFileSync(path.join(directory,`${language}-${mode}.js`),'utf8');
    let events=[];
    function run(host,data,blocked=false){
      events=[];
      return vm.runInNewContext(script,{
        location:{hostname:host,protocol:'https:',href:`https://${host}/unchanged-path`},
        window:{dispatchEvent(event){events.push(event);}},
        StorageEvent:class {constructor(type,details){this.type=type;Object.assign(this,details);}},
        localStorage:{getItem(k){if(blocked)throw Error('Storage denied');return data.get(k)??null;},setItem(k,v){data.set(k,v);}}
      });
    }
    const data=new Map([['wip-form-design','pdf'],['unrelated','keep'],['wip-language','old'],['wip-theme-mode','old']]);
    assert.equal(run('ludicrous-glitter-antitoxic.ngrok-free.dev',data),true);
    assert.equal(data.get('wip-language'),expectedLanguage);
    assert.equal(data.get('wip-theme-mode'),expectedMode);
    assert.equal(data.get('wip-form-design'),'pdf');
    assert.equal(data.get('unrelated'),'keep');
    assert.equal(data.size,4);
    assert.equal(events.length,2);
    assert.equal(events[0].key,'wip-language');assert.equal(events[0].newValue,expectedLanguage);
    assert.equal(events[1].key,'wip-theme-mode');assert.equal(events[1].newValue,expectedMode);
    assert.equal(run('ludicrous-glitter-antitoxic.ngrok-free.dev',data),false);
    assert.equal(events.length,0);
    for(const host of ['example.com','evilngrok-free.dev','x.ngrok-free.dev.evil.com']) {
      const untouched=new Map([['wip-form-design','pdf']]);
      assert.equal(run(host,untouched),false);assert.deepEqual([...untouched],[['wip-form-design','pdf']]);
      const languageOnly=new Map([['wip-language','site-choice']]);
      assert.equal(run(host,languageOnly),true);assert.deepEqual([...languageOnly],[['wip-language',expectedLanguage]]);
      const themeOnly=new Map([['wip-theme-mode','']]);
      assert.equal(run(host,themeOnly),true);assert.deepEqual([...themeOnly],[['wip-theme-mode',expectedMode]]);
    }
    const fresh=new Map();assert.equal(run('test.ngrok-free.dev',fresh),true);
    assert.deepEqual([...fresh],[['wip-language',expectedLanguage],['wip-theme-mode',expectedMode]]);
    assert.equal(run('test.ngrok-free.dev',new Map(),true),false);
    passed++;
  }
}
console.log(`PASS: ${passed} storage combinations; ngrok scope, preservation, idempotence, and denied-storage handling`);
