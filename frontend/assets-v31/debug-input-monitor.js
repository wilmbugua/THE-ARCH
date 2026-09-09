(function(){
  async function send(ev){
    try { await fetch('/api/_debug', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify(ev)}); } catch(e) { console.log('debug-send-failed',e) }
  }
  document.addEventListener('click', function(e){
    const s = {type:'click', tag: e.target.tagName, id: e.target.id || null, class: e.target.className || null, text: (e.target.innerText||'').slice(0,100)};
    send(s);
  }, true);
  document.addEventListener('keydown', function(e){
    const s = {type:'keydown', key: e.key, code: e.code, targetId: e.target && e.target.id, targetClass: e.target && e.target.className};
    send(s);
  }, true);
  document.addEventListener('input', function(e){
    const s = {type:'input', value: e.target && e.target.value, id: e.target && e.target.id, name: e.target && e.target.name};
    send(s);
  }, true);
  console.log('debug-input-monitor installed');
})();
