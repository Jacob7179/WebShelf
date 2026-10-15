/* WebShelf media-session bridge. Runs at document start in eligible frames. */
(function(){
 'use strict';
 if(window.__wsMediaInstalled)return;
 window.__wsMediaInstalled=true;
 var handlers={}, last='', playing=false, available=false, lastActive=0, previousPositionSent=0;
 var frameId=Math.random().toString(36).slice(2)+Date.now().toString(36);
 function elements(){return Array.prototype.slice.call(document.querySelectorAll('audio,video'));}
 function active(){var a=elements();return a.find(function(m){return !m.paused&&!m.ended;}) || a[0];}
 function musicSite(){return location.hostname==='jacob7179.github.io' && location.pathname.indexOf('/YouTube-Music-Player-Web')===0;}
 function sampleState(){
   if(!musicSite())return null;
   try{
     var ready=(typeof player!=='undefined' && player && typeof player.getPlayerState==='function');
     var state=ready?Number(player.getPlayerState()):-1;
     var song=typeof getCurrentSongObject==='function'?getCurrentSongObject():null;
     var name=song?(song.songName||song.title||'YouTube Music Player'):document.title;
     var artist=song?(song.authorName||''):'';
     return {playing:state===1 || state===3,available:ready||!!song,title:name,artist:artist,position:ready?Number(player.getCurrentTime()||0):0,duration:ready?Number(player.getDuration()||0):0,artwork:song?(song.albumArt|| (song.videoId?'https://i.ytimg.com/vi/'+encodeURIComponent(song.videoId)+'/hqdefault.jpg':'')):''};
   }catch(e){return null;}
 }
 function notify(force){
   var dedicated=sampleState();
   var a=elements(), m=active();
   playing=a.some(function(e){return !e.paused&&!e.ended;});
   available=!!m || !!handlers.play || !!handlers.pause;
   if(dedicated){playing=dedicated.playing;available=dedicated.available;}
   if(playing)lastActive=Date.now();
   var title=dedicated?dedicated.title:(document.title||location.hostname);
   var artist=dedicated?dedicated.artist:'';var artwork=dedicated?dedicated.artwork:'';
   try{if(!dedicated&&navigator.mediaSession&&navigator.mediaSession.metadata){var md=navigator.mediaSession.metadata;if(md.title)title=md.title;if(md.artist)artist=md.artist;if(md.artwork&&md.artwork.length)artwork=md.artwork[md.artwork.length-1].src||'';}}catch(e){}
   if(!artwork){try{var cover=document.querySelector('meta[property="og:image"],meta[name="twitter:image"]');if(cover)artwork=cover.content||'';}catch(e){}}
   // Resolve relative website art to an absolute HTTPS URL for Android's downloader.
   try{if(artwork)artwork=new URL(artwork,location.href).href;}catch(e){artwork='';}
   var position=dedicated?dedicated.position:(m&&isFinite(m.currentTime)?m.currentTime:0);
   var duration=dedicated?dedicated.duration:(m&&isFinite(m.duration)?m.duration:0);
   try{if(!dedicated&&navigator.mediaSession&&navigator.mediaSession.__wsPosition){var p=navigator.mediaSession.__wsPosition;position=p.position||position;duration=p.duration||duration;}}catch(e){}
   var positionMs=Math.max(0,Math.round(Number(position||0)*1000));
   var durationMs=Math.max(0,Math.round(Number(duration||0)*1000));
   var key=title+'|'+artist+'|'+artwork+'|'+playing+'|'+available+'|'+durationMs+'|'+Math.floor(positionMs/2000);
   if(!force&&key===last)return;last=key;
   try{WebShelfMedia.update(frameId,String(title).slice(0,180),String(artist).slice(0,180),String(artwork).slice(0,1200),playing,available,positionMs,durationMs);}catch(e){}
 }
 function command(cmd){
   if(cmd!=='play'&&cmd!=='pause'&&cmd!=='nexttrack'&&cmd!=='previoustrack')return;
   if(musicSite()){try{if(typeof handleSystemMediaAction==='function'){handleSystemMediaAction(cmd);setTimeout(function(){notify(true);},300);return;}}catch(e){}}
   // Prefer the website's own registered Media Session action handler.
   var h=handlers[cmd];
   if(h){try{h.call(navigator.mediaSession,{action:cmd});notify(true);return;}catch(e){}}
   var m=active();
   if(m){try{if(cmd==='play'){var p=m.play();if(p&&p.catch)p.catch(function(){});}else if(cmd==='pause')m.pause();}catch(e){}}
   setTimeout(function(){notify(true);},250);
 }
 function seek(ms){
   var seconds=Math.max(0,Number(ms)/1000);if(!isFinite(seconds))return;
   if(musicSite()){try{if(typeof handleSystemMediaAction==='function'){handleSystemMediaAction('seekto',{seekTime:seconds});notify(true);return;}}catch(e){}}
   if(handlers.seekto){try{handlers.seekto({action:'seekto',seekTime:seconds});notify(true);return;}catch(e){}}
   var m=active();if(m&&isFinite(m.duration)){try{m.currentTime=Math.min(seconds,m.duration);}catch(e){}}
   notify(true);
 }
 window.__webshelfMediaSeek=seek;
 window.__webshelfMediaRefresh=function(){last='';notify(true);};
 window.__webshelfMediaCommand=command;
 window.addEventListener('message',function(e){if(e.data&&e.data.__webshelfMediaRefresh){window.__webshelfMediaRefresh();return;}
   if(e.data&&typeof e.data.__webshelfMediaSeek==='number'){seek(e.data.__webshelfMediaSeek);return;}
   if(e.data&&e.data.__webshelfMediaCommand){command(e.data.__webshelfMediaCommand);try{for(var i=0;i<window.frames.length;i++)window.frames[i].postMessage(e.data,'*');}catch(x){}}});
 try{
   var proto=window.MediaSession&&MediaSession.prototype;
   if(proto&&proto.setPositionState){
     var originalPosition=proto.setPositionState;
     proto.setPositionState=function(state){try{this.__wsPosition=state;}catch(e){}notify(false);return originalPosition.apply(this,arguments);};
   }
   if(proto&&proto.setActionHandler){
     var original=proto.setActionHandler;
     proto.setActionHandler=function(action,fn){handlers[action]=typeof fn==='function'?fn:null;notify(true);return original.apply(this,arguments);};
   }
 }catch(e){}
 ['play','playing','pause','ended','loadedmetadata','emptied'].forEach(function(event){document.addEventListener(event,function(){notify(true);},true);});
 // Metadata changes are cheap; avoid observing whole subtrees of large websites.
 var head=document.head||document.documentElement;
 if(head)new MutationObserver(function(){notify(false);}).observe(head,{childList:true,subtree:false,attributes:false});
 window.addEventListener('pageshow',function(){notify(true);});
 document.addEventListener('visibilitychange',function(){notify(true);});
 setInterval(function(){notify(false);},1500);
 notify(true);
})();
