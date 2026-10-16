/* WebShelf YouTube page-level ad mitigation. Runs only on YouTube origins.
   Best effort: does not decrypt/intercept HTTPS video streams. */
(function(){
 'use strict';
 if(window.__wsAdMitigation)return;
 window.__wsAdMitigation=true;
 function enabled(){try{return !!WebShelfAdBlock.isEnabled();}catch(e){return false;}}
 var selectors=['.ytp-ad-overlay-container','.ytp-ad-overlay-slot','.ytp-ad-player-overlay','.ytp-ad-survey','.ytp-ad-image-overlay','.ytd-display-ad-renderer','ytd-display-ad-renderer','ytd-promoted-video-renderer','ytd-ad-slot-renderer','ytm-promoted-sparkles-web-renderer','ytmusic-mealbar-promo-renderer'];
 function conceal(){
   if(!enabled())return;
   selectors.forEach(function(s){try{document.querySelectorAll(s).forEach(function(e){e.style.setProperty('display','none','important');});}catch(x){}});
   // Do not hide the video element or player: this would hide real content too.
   var adPlaying=!!document.querySelector('.html5-video-player.ad-showing,.html5-video-player.ad-interrupting');
   if(!adPlaying)return;
   var btn=document.querySelector('.ytp-skip-ad-button,.ytp-ad-skip-button,.ytp-ad-skip-button-modern,button.ytp-ad-skip-button-modern');
   if(btn && !btn.disabled){try{btn.click();}catch(x){}}
 }
 function watch(){conceal();}
 // SPA navigation and dynamic player changes.
 document.addEventListener('yt-navigate-finish',watch,true);
 document.addEventListener('yt-page-data-updated',watch,true);
 document.addEventListener('DOMContentLoaded',function(){
   watch();
   if(document.documentElement){new MutationObserver(function(){watch();}).observe(document.documentElement,{childList:true,subtree:true});}
 });
 setInterval(watch,1000);
})();
