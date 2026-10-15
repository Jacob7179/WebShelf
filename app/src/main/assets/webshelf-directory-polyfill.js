(function(){
  if(window.__wsDirectoryInstalled && typeof window.showDirectoryPicker==='function')return;
  window.__wsDirectoryInstalled=true;
  var token=__TOKEN__, pending=null;
  var wsHandles=new Map(), nextHandle=0;
  function register(handle,id){id=id||('ws:'+token+':'+(++nextHandle)); Object.defineProperty(handle,'__wsHandleId',{value:id}); wsHandles.set(id,handle); return handle;}
  function abort(){return new DOMException('Folder selection cancelled','AbortError');}
  function directory(name){return {kind:'directory',name:name,children:new Map()};}
  function expose(node,path,folderId){
    path=path||'';
    if(node.kind==='file')return register({kind:'file',name:node.name,isSameEntry:async function(other){return other&&other.__wsid===node.id;},__wsid:node.id,queryPermission:async function(){return 'granted';},requestPermission:async function(){return 'granted';},getFile:async function(){
      var parts=[],size=Math.max(0,node.size),pos=0;
      while(pos<size){
        var data=WebShelfFolder.readChunk(token,node.id,pos,Math.min(131072,size-pos));
        if(!data)throw new DOMException('Unable to read file','NotReadableError');
        var bytes=Uint8Array.from(atob(data),function(c){return c.charCodeAt(0);});
        if(!bytes.length)throw new DOMException('Unexpected end of file','NotReadableError');
        parts.push(bytes);pos+=bytes.length;
      }
      return new File(parts,node.name,{type:node.mime||'',lastModified:node.time||Date.now()});
    }}, folderId?'ws:p:'+folderId+':'+encodeURIComponent(path):null);
    var handle={kind:'directory',name:node.name,
      queryPermission:async function(){return 'granted';},requestPermission:async function(){return 'granted';},
      getDirectoryHandle:async function(name){var x=node.children.get(name);if(!x||x.kind!=='directory')throw new DOMException('Directory not found','NotFoundError');return expose(x,path+'/'+name,folderId);},
      getFileHandle:async function(name){var x=node.children.get(name);if(!x||x.kind!=='file')throw new DOMException('File not found','NotFoundError');return expose(x,path+'/'+name,folderId);},
      entries:async function*(){for(var pair of node.children)yield [pair[0],expose(pair[1],path+'/'+pair[0],folderId)];},
      keys:async function*(){for(var name of node.children.keys())yield name;},
      values:async function*(){for(var item of node.children.values())yield expose(item,path+'/'+item.name,folderId);},
      resolve:async function(other){return other===handle?[]:null;},
      isSameEntry:async function(other){return other===handle;}
    };
    handle[Symbol.asyncIterator]=handle.entries;
    return register(handle,folderId?'ws:p:'+folderId+':'+encodeURIComponent(path):null);
  }
  function buildRoot(result){
    var root=directory(result.name||'Selected folder');
    for(var entry of result.entries){
      var parts=entry.p.split('/').filter(Boolean),parent=root;
      for(var i=0;i<parts.length-1;i++){
        if(!parent.children.has(parts[i]))parent.children.set(parts[i],directory(parts[i]));
        parent=parent.children.get(parts[i]);
      }
      var leaf=parts[parts.length-1];if(!leaf)continue;
      if(entry.d){if(!parent.children.has(leaf))parent.children.set(leaf,directory(leaf));}
      else parent.children.set(leaf,{kind:'file',name:leaf,id:entry.id,size:entry.size,time:entry.time,mime:entry.mime});
    }
    return root;
  }
  function reviveHandle(id){
    if(wsHandles.has(id))return wsHandles.get(id);
    if(typeof id!=='string'||!id.startsWith('ws:p:'))return null;
    var match=/^ws:p:([^:]+):(.*)$/.exec(id);if(!match)return null;
    var json=WebShelfFolder.restore(token,match[1]);if(!json)return null;
    var result=JSON.parse(json),root=buildRoot(result),path=decodeURIComponent(match[2]);
    var node=root;
    for(var name of path.split('/').filter(Boolean)){node=node.children&&node.children.get(name);if(!node)return null;}
    return expose(node,path,match[1]);
  }
  window.__wsDirectoryFinish=function(result){
    var deferred=pending;pending=null;if(!deferred)return;
    if(!result){deferred.reject(abort());return;}
    try{
      var root=buildRoot(result);
      var handle=expose(root,'',result.folderId);
      deferred.resolve(handle);
    }catch(e){console.error('WebShelf: failed to construct folder handles',e);deferred.reject(e);}
  };
  // Android WebView does not offer showDirectoryPicker; install an API-compatible
  // read-only handle backed by the Android Storage Access Framework.
  // Override WebView's API even when it exists but is unsupported or throws.
  // FileSystemHandle objects from this bridge are ordinary JavaScript objects.
  // IndexedDB cannot structured-clone functions; store a clone-safe token instead,
  // then restore its live handle for reads in the current page session.
  // This avoids a DataCloneError when apps save a directory handle during import.
  var marker='__webshelfDirectoryReference__';
  function transform(value,encode,seen){
    if(value===null||typeof value!=='object')return value;
    if(encode&&value.__wsHandleId)return {[marker]:value.__wsHandleId};
    if(!encode&&Object.prototype.hasOwnProperty.call(value,marker)&&typeof value[marker]==='string')return reviveHandle(value[marker])||value;
    if(value instanceof Blob||value instanceof Date||value instanceof RegExp||value instanceof ArrayBuffer||ArrayBuffer.isView(value))return value;
    if(seen.has(value))return seen.get(value);
    if(Array.isArray(value)){
      var arr=[];seen.set(value,arr);for(var item of value)arr.push(transform(item,encode,seen));return arr;
    }
    if(value instanceof Map){var m=new Map();seen.set(value,m);for(var pair of value)m.set(transform(pair[0],encode,seen),transform(pair[1],encode,seen));return m;}
    if(value instanceof Set){var set=new Set();seen.set(value,set);for(var entry of value)set.add(transform(entry,encode,seen));return set;}
    if(Object.getPrototypeOf(value)!==Object.prototype&&Object.getPrototypeOf(value)!==null)return value;
    var copy={};seen.set(value,copy);
    for(var key of Object.keys(value))copy[key]=transform(value[key],encode,seen);
    return copy;
  }
  try{
    for(var method of ['put','add']){
      var original=IDBObjectStore.prototype[method];
      IDBObjectStore.prototype[method]=function(value){
        var replacement=transform(value,true,new WeakMap());
        var args=Array.prototype.slice.call(arguments);args[0]=replacement;
        return original.apply(this,args);
      };
    }
    // Native IDBRequest.result is read-only; replace only the getter on the
    // prototype.  No changes are made to successful results without our marker.
    var resultDesc=Object.getOwnPropertyDescriptor(IDBRequest.prototype,'result');
    if(resultDesc&&resultDesc.configurable&&typeof resultDesc.get==='function'){
      Object.defineProperty(IDBRequest.prototype,'result',{
        configurable:true,enumerable:resultDesc.enumerable,
        get:function(){var value=resultDesc.get.call(this);return transform(value,false,new WeakMap());}
      });
    }
    // Dexie and similar wrappers may read cursor.value, not request.result.
    var cursorValue=Object.getOwnPropertyDescriptor(IDBCursor.prototype,'value');
    if(cursorValue&&cursorValue.configurable&&typeof cursorValue.get==='function'){
      Object.defineProperty(IDBCursor.prototype,'value',{
        configurable:true,enumerable:cursorValue.enumerable,
        get:function(){return transform(cursorValue.get.call(this),false,new WeakMap());}
      });
    }
  }catch(error){console.warn('WebShelf: optional IndexedDB handle compatibility unavailable',error);}
  window.showDirectoryPicker=function(){
    if(pending)return Promise.reject(new DOMException('Picker already open','InvalidStateError'));
    return new Promise(function(resolve,reject){pending={resolve:resolve,reject:reject};WebShelfFolder.chooseApi(token);});
  };
})();