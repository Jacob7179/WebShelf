const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const script=fs.readFileSync(process.argv[2],'utf8');
function page(pathname,initial){
  const source='Production workflow & traceability';
  const values=new Map(initial?[['wip-language',initial]]:[]);
  values.set('wip-form-design','pdf');
  const listeners={};
  const element={nodeType:1,closest(){return null;},hasAttribute(){return false;},setAttribute(){},removeAttribute(){}};
  const text={nodeType:3,parentElement:element,nodeValue:source};
  const location={pathname,search:'?keep=1',hash:'#section',href:'https://writing-jelly-submerge.ngrok-free.dev'+pathname+'?keep=1#section',assign(){throw Error('Unexpected URL change');},replace(){throw Error('Unexpected URL change');}};
  const document={body:element,title:source,readyState:'complete',documentElement:{setAttribute(){},removeAttribute(){}},querySelectorAll(){return [];},createTreeWalker(){let read=false;return{nextNode(){if(read)return null;read=true;return text;}};},dispatchEvent(){},addEventListener(){}};
  const localStorage={getItem:k=>values.get(k)??null,setItem:(k,v)=>values.set(k,v)};
  const window={location,clearTimeout(){},addEventListener(name,handler){listeners[name]=handler;}};
  const context={window,document,localStorage,Node:{TEXT_NODE:3,ELEMENT_NODE:1,DOCUMENT_FRAGMENT_NODE:11},NodeFilter:{SHOW_ELEMENT:1,SHOW_TEXT:4,FILTER_REJECT:2,FILTER_ACCEPT:1},HTMLInputElement:class{},MutationObserver:class{observe(){}},CustomEvent:class{},URL,console};
  vm.runInNewContext(script,context);
  return{window,document,text,source,localStorage,values,listeners,location};
}
let checks=0;
for(const route of ['/','/en/records','/zh-cn/records','/login','/ms/login','/quick-login']){
  const p=page(route,'zh-cn');
  assert.equal(p.window.WIPLanguage.getLanguage(),'zh-cn');
  assert.equal(p.values.get('wip-language'),'zh-cn');
  assert.notEqual(p.text.nodeValue,p.source);
  assert.equal(p.location.pathname,route);
  p.window.WIPLanguage.setLanguage('ms');
  assert.equal(p.window.WIPLanguage.getLanguage(),'ms');
  assert.equal(p.values.get('wip-language'),'ms');
  assert.equal(p.text.nodeValue,p.window.WIPLanguage.t(p.source));
  p.window.WIPLanguage.setLanguage('en');
  assert.equal(p.text.nodeValue,p.source);assert.equal(p.document.title,p.source);
  p.localStorage.setItem('wip-language','zh-cn');
  p.listeners.storage({key:'wip-language',storageArea:p.localStorage});
  assert.equal(p.window.WIPLanguage.getLanguage(),'zh-cn');
  assert.equal(p.text.nodeValue,p.window.WIPLanguage.t(p.source));
  assert.equal(p.location.pathname,route);assert.equal(p.location.search,'?keep=1');assert.equal(p.location.hash,'#section');
  assert.equal(p.values.get('wip-form-design'),'pdf');checks++;
}
const fresh=page('/ms/records',null);assert.equal(fresh.window.WIPLanguage.getLanguage(),'ms');
const invalid=page('/en/records','invalid');assert.equal(invalid.window.WIPLanguage.getLanguage(),'en');
console.log(`PASS: ${checks} routes; stored preference, Chinese/Malay/English switching, storage events, unchanged URLs, first-visit fallback`);
