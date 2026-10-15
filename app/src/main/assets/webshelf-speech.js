(function(){
  if(window.__wsSpeechShimInstalled)return;
  if(!window.WebShelfSpeech)return;
  window.__wsSpeechShimInstalled=true;
  var active=null;
  function dispatch(instance,type,details){
    var event=details||{type:type};
    try{if(typeof instance['on'+type]==='function')instance['on'+type](event);}catch(e){console.error(e);}
    var listeners=instance._listeners[type]||[];
    listeners.slice().forEach(function(fn){try{fn.call(instance,event);}catch(e){console.error(e);}});
  }
  function SpeechRecognition(){
    this.lang=navigator.language;this.continuous=false;this.interimResults=false;this.maxAlternatives=1;
    this._listeners={};
  }
  SpeechRecognition.prototype.addEventListener=function(type,fn){if(typeof fn==='function')(this._listeners[type]||(this._listeners[type]=[])).push(fn);};
  SpeechRecognition.prototype.removeEventListener=function(type,fn){var arr=this._listeners[type]||[];var index=arr.indexOf(fn);if(index!==-1)arr.splice(index,1);};
  SpeechRecognition.prototype.start=function(){
    if(active)throw new DOMException('Recognition already started','InvalidStateError');
    active=this;
    dispatch(this,'start');
    WebShelfSpeech.start(this.lang || navigator.language);
  };
  SpeechRecognition.prototype.stop=function(){if(active===this)WebShelfSpeech.stop();};
  SpeechRecognition.prototype.abort=function(){if(active===this)WebShelfSpeech.abort();};
  window.__wsSpeechEvent=function(type){if(active)dispatch(active,type);};
  window.__wsSpeechDone=function(text,error){
    var instance=active;active=null;if(!instance)return;
    if(text!==null){
      var alternative={transcript:text,confidence:1};
      var segment=[alternative];segment.isFinal=true;
      var results=[segment];results.item=function(i){return results[i];};
      dispatch(instance,'result',{type:'result',resultIndex:0,results:results});
    }else dispatch(instance,'error',{type:'error',error:error||'no-speech',message:error||'no-speech'});
    dispatch(instance,'end');
  };
  if (location.hostname === 'translate.google.com' ||
      (!window.SpeechRecognition && !window.webkitSpeechRecognition)) {
    window.SpeechRecognition=SpeechRecognition;
    window.webkitSpeechRecognition=SpeechRecognition;
  }
})();
