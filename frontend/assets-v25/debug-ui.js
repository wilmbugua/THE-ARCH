(function(){
  const panel = document.createElement('div');
  panel.id = 'kalc-debug-panel';
  Object.assign(panel.style, {
    position: 'fixed', right: '10px', bottom: '10px', width: '320px', maxHeight: '40vh', overflow: 'auto',
    background: 'rgba(0,0,0,0.7)', color: '#fff', fontSize: '12px', padding: '8px', zIndex: 2147483647, borderRadius: '6px'
  });
  const title = document.createElement('div'); title.textContent = 'DEBUG: input events'; title.style.fontWeight='600'; title.style.marginBottom='6px';
  panel.appendChild(title);
  const list = document.createElement('div'); panel.appendChild(list);
  document.addEventListener('click', (e)=>{
    const info = {t: new Date().toISOString(), type: 'click', tag: e.target.tagName, id: e.target.id||null, cls: e.target.className||null, txt: (e.target.innerText||'').slice(0,80)};
    push(info);
    flash(e.target);
    console.log('debug-click',info);
  }, true);
  document.addEventListener('input', (e)=>{
    const info = {t: new Date().toISOString(), type: 'input', id: e.target.id||null, name: e.target.name||null, val: (e.target.value||'').toString().slice(0,80)};
    push(info);
    console.log('debug-input',info);
  }, true);
  document.addEventListener('keydown', (e)=>{
    const info = {t: new Date().toISOString(), type: 'keydown', key: e.key, code: e.code, target: e.target && (e.target.id||e.target.className||e.target.tagName)};
    push(info);
    console.log('debug-key',info);
    if (e.key === 'Enter') {
      // also show nearest button
      const btn = document.activeElement && document.activeElement.closest && document.activeElement.closest('button');
      if (btn) { push({t: new Date().toISOString(), type: 'enter-activates', button: btn.innerText?.slice(0,40)||null}); flash(btn); }
    }
  }, true);

  function push(obj){
    const row = document.createElement('div'); row.style.borderTop='1px solid rgba(255,255,255,0.06)'; row.style.padding='6px 0';
    row.textContent = `${obj.t} · ${obj.type} · ${obj.tag||obj.key||''} ${obj.id?(' #'+obj.id):''} ${obj.cls?(' '+obj.cls):''} ${obj.txt||obj.val||obj.button||''}`;
    list.insertBefore(row, list.firstChild);
    // keep max 20
    while(list.childElementCount>20) list.removeChild(list.lastChild);
    // expose last events
    window.__KALC_DEBUG_EVENTS = window.__KALC_DEBUG_EVENTS || [];
    window.__KALC_DEBUG_EVENTS.unshift(obj);
    if (window.__KALC_DEBUG_EVENTS.length>200) window.__KALC_DEBUG_EVENTS.pop();
  }
  function flash(el){
    if (!el) return; const orig = el.style.transition; const origBg = el.style.outline;
    el.style.outline = '3px solid rgba(255,200,0,0.9)'; el.style.transition = 'outline 120ms ease-in';
    setTimeout(()=>{ try { el.style.outline = origBg||''; el.style.transition = orig||'' } catch(e){} }, 600);
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function() { document.body.appendChild(panel); console.log('kalc debug UI installed'); });
  } else {
    document.body.appendChild(panel);
    console.log('kalc debug UI installed');
  }
})();
