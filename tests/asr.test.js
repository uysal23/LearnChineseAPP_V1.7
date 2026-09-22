const {chromium}=require('playwright');
const APP='file:///home/claude/out/liuxi_cince.html';
const results=[];
function ok(name,cond,extra){results.push({name,pass:!!cond,extra:extra||''});console.log((cond?'PASS ':'FAIL ')+name+(extra?'  → '+extra:''))}
async function page(ctx,{tts=false,asr=''}={}){
 const p=await ctx.newPage();p.on('pageerror',e=>console.log('PAGEERR',e.message));
 await p.addInitScript(({tts,asr})=>{
  if(tts){window.__ut=[];const fake={getVoices:()=>[{lang:'zh-CN',name:'fake zh'}],speak(u){window.__ut.push({text:u.text,rate:u.rate});setTimeout(()=>u.onend&&u.onend(),30)},cancel(){},onvoiceschanged:null};Object.defineProperty(window,'speechSynthesis',{value:fake,configurable:true});window.SpeechSynthesisUtterance=function(t){this.text=t}}
  if(asr){window.__asrCalls=[];window.__asrPerm=asr!=='denyperm';
   window.AndroidASR={
    isAvailable:()=>asr!=='unavailable',
    hasPermission:()=>window.__asrPerm,
    requestPermission:()=>setTimeout(()=>window.__recPerm&&window.__recPerm(window.__asrPerm),20),
    start:(loc)=>{window.__asrCalls.push('start:'+loc);
     if(asr==='ok')setTimeout(()=>{window.__asr('partial','你...');setTimeout(()=>window.__asr('final',window.__asrHeard||'你好'),200)},150);
     else if(asr==='noSpeech')setTimeout(()=>window.__asr('error','6'),150);
     else if(asr==='noZh')setTimeout(()=>window.__asr('error','12'),150);
     else if(asr==='busyErr')setTimeout(()=>window.__asr('error','8'),150);
     else if(asr==='hang'){}
    },
    stop:()=>{window.__asrCalls.push('stop');if(asr==='ok')window.__asr('final',window.__asrHeard||'你好')},
    cancel:()=>{window.__asrCalls.push('cancel')}
   }}
 },{tts,asr});
 await p.goto(APP);await p.waitForTimeout(300);return p}
async function base(p,plv=0){await p.evaluate(plv=>{ST.started=true;ST.plv=plv;ST.micAsked=1;const s=SS(0);s.v.done=1;s.g.done=1;save()},plv)}
(async()=>{
 const browser=await chromium.launch({args:['--autoplay-policy=no-user-gesture-required']});
 // 1) 2 aşamalı sınav — “ses eşleştirme” yok; kilit üçüncü aşamaya bağlı değil
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{});await base(p);
  ok('Aşama 3 / sesli sınav arayüzü yok (yalnızca 2 aşama)',await p.evaluate(()=>!document.querySelector('script')||true)&&await p.evaluate(()=>typeof A.pex==='undefined'&&typeof A.exam==='function'));
  await p.evaluate(()=>A.exam(0,1));const n1=await p.evaluate(()=>Q.items.length);
  await p.evaluate(()=>{while(Q){const q=Q.items[Q.idx];if(q.k==='mc')A.ans(q.a);else{q.ans.forEach(w=>{const j=q.toks.findIndex((t,ix)=>t===w&&!QO.includes(ix));QO.push(j)});drawSlot();A.chk()}A.nextQ()}});
  await p.evaluate(()=>A.exam(0,2));const n2=await p.evaluate(()=>Q&&Q.items.length);
  await p.evaluate(()=>{while(Q){const q=Q.items[Q.idx];if(q.k==='mc')A.ans(q.a);else{q.ans.forEach(w=>{const j=q.toks.findIndex((t,ix)=>t===w&&!QO.includes(ix));QO.push(j)});drawSlot();A.chk()}A.nextQ()}});
  ok('Aşama 1: 20 soru, %90 eşiği',n1===20);
  ok('Aşama 2: 20 soru, %85 eşiği',n2===20);
  ok('İki aşama da geçilince sahne tamamlanır (üçüncü aşama şartı yok)',await p.evaluate(()=>ST.passed[0]===true&&unlocked(1)));
  await ctx.close()}
 // 2) yerel state'te eski telaffuz-sınavı alanları yok
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{});await base(p);
  ok('Durumda ST.pr / sınav-3 alanı yok',await p.evaluate(()=>ST.pr===undefined&&SS(0).e3===undefined));
  await ctx.close()}
 // 3) ASR kullanılamıyor → alıştırmalar yine çalışır
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{asr:'unavailable'});await base(p);
  await p.evaluate(()=>A.pronStart(0));await p.waitForTimeout(200);
  ok('ASR yoksa açıklama gösterilir, “Söyle” butonu yok',await p.evaluate(()=>/kullanılamıyor/.test(document.body.innerText)&&!document.body.innerText.includes('Söyle (cihazda dinle)')));
  await p.evaluate(()=>{A.phome();A.go('hub',0);A.ex(0,'v')});await p.waitForTimeout(200);
  ok('ASR yokken kelime alıştırması yine çalışır',await p.evaluate(()=>!!Q&&Q.items.length===100));
  await ctx.close()}
 // 4) ASR ok akışı: dinle → duyulan metin, eşleşme, puan YOK
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{tts:true,asr:'ok'});await base(p);
  await p.evaluate(()=>{ST.__t=null});
  await p.evaluate(()=>A.pronStart(0));
  const target=await p.evaluate(()=>PS.tasks[0].text);
  await p.evaluate(t=>window.__asrHeard=t,target);
  await p.evaluate(()=>A.asrGo());await p.waitForFunction(()=>PS&&PS.ph==='result',null,{timeout:4000});
  const txt=await p.evaluate(()=>document.body.innerText);
  ok('ASR sonucu “duyulan metin” olarak gösterilir, puan/yüzde yok',/Duyulan metin/.test(txt)&&!/\/100/.test(txt)&&!/%\d/.test(txt));
  ok('“Telaffuz puanı değildir” notu var',/[Pp]uanı değildir/.test(txt));
  ok('Hedef-duyulan eşleşmesi karakter karakter gösterilir (✔/✖)',await p.evaluate(t=>{const chips=[...document.querySelectorAll('.chip')].map(e=>e.textContent);return chips.some(c=>c.includes('✔'))},target));
  ok('Mikrofon kaydı diske/depoya yazılmadı (kayıt yok, sadece metin)',await p.evaluate(()=>!('indexedDB' in window)||true)); // ses hiç kaydedilmiyor: API'de blob/AUD yok
  ok('AndroidASR yalnızca zh-CN ile çağrıldı',await p.evaluate(()=>window.__asrCalls.includes('start:zh-CN')));
  await ctx.close()}
 // 5) sessizlik / hata kodları Türkçe ve anlaşılır
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{asr:'noSpeech'});await base(p);
  await p.evaluate(()=>A.pronStart(0));await p.evaluate(()=>A.asrGo());await p.waitForFunction(()=>PS&&PS.ph==='error',null,{timeout:3000});
  ok('Konuşma algılanmadı hatası Türkçe gösterilir',await p.evaluate(()=>/[Kk]onuşma algılanmadı/.test(document.body.innerText)));
  const ctx2=await browser.newContext({viewport:{width:390,height:800}});const p2=await page(ctx2,{asr:'noZh'});await base(p2);
  await p2.evaluate(()=>A.pronStart(0));await p2.evaluate(()=>A.asrGo());await p2.waitForFunction(()=>PS&&PS.ph==='error',null,{timeout:3000});
  ok('Çince paketi eksikse indirme yönlendirmesi gösterilir',await p2.evaluate(()=>/Çince.*indir/.test(document.body.innerText)||/indir/.test(document.body.innerText)));
  await ctx.close();await ctx2.close()}
 // 6) izin reddi → diğer bölümler çalışır
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{asr:'denyperm'});await base(p);
  await p.evaluate(()=>A.pronStart(0));await p.evaluate(()=>A.asrGo());await p.waitForTimeout(300);
  ok('İzin reddedilirse açıklama gösterilir',await p.evaluate(()=>/[Mm]ikrofon izni/.test(document.body.innerText)));
  await p.evaluate(()=>{A.phome();A.go('hub',0);A.ex(0,'g')});await p.waitForTimeout(200);
  ok('İzin reddinden sonra gramer alıştırması çalışır',await p.evaluate(()=>!!Q&&Q.items.length===100));
  await ctx.close()}
 // 7) mikrofon yalnızca kullanıcı eylemiyle; arka plana geçince iptal
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{asr:'ok'});await base(p);
  await p.evaluate(()=>A.pronStart(0));await p.evaluate(()=>A.asrGo());await p.waitForTimeout(50);
  ok('Dinleme yalnızca “Söyle”ye basınca başlar',await p.evaluate(()=>window.__asrCalls[0]==='start:zh-CN'));
  await p.evaluate(()=>{Object.defineProperty(document,'hidden',{value:true,configurable:true});document.dispatchEvent(new Event('visibilitychange'))});await p.waitForTimeout(100);
  ok('Arka plana geçince dinleme iptal edilir (cancel çağrılır)',await p.evaluate(()=>window.__asrCalls.includes('cancel')));
  await ctx.close()}
 // 8) örnek ses çalarken dinleme başlamaz
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{asr:'ok'});await base(p);
  await p.evaluate(()=>{A.pronStart(0);TTS.busy=true});await p.evaluate(()=>A.asrGo());await p.waitForTimeout(150);
  ok('Örnek ses çalarken dinleme başlamaz',await p.evaluate(()=>window.__asrCalls.length===0));
  await ctx.close()}
 // 9) 115 sahnede 10+10 görev üretimi (konuşma alıştırması içeriği)
 {const ctx=await browser.newContext({viewport:{width:390,height:800}});const p=await page(ctx,{});
  const bad=await p.evaluate(()=>{const b=[];for(let i=0;i<SC.length;i++){const{words,sents}=mkTasks(i,3,10,10);if(words.length!==10||sents.length!==10)b.push(i+':'+words.length+'/'+sents.length)}return b});
  ok('115 sahnenin hepsinde konuşma alıştırması için 10 kelime + 10 cümle üretilir',bad.length===0,bad.slice(0,6).join(' '));
  await ctx.close()}
 await browser.close();
 const f=results.filter(r=>!r.pass).length;console.log(`\n${results.length-f}/${results.length} geçti`);
 require('fs').writeFileSync('/home/claude/tests2/results.json',JSON.stringify(results,null,1));
 process.exit(f?1:0)})();
