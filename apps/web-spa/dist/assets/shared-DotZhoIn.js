import{c,j as s,k as A,x as h,e as E,s as y,B as i}from"./index-BxOpnQFX.js";import{u as g}from"./useMutation-CJXNWvI6.js";/**
 * @license lucide-react v1.63.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const d={name:"pause",size:24,node:[["rect",{x:"14",y:"3",width:"5",height:"18",rx:"1",key:"kaeet6"}],["rect",{x:"5",y:"3",width:"5",height:"18",rx:"1",key:"1wsw3u"}]]};d.node;const k=c(d);/**
 * @license lucide-react v1.63.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const u={name:"play",size:24,node:[["path",{d:"M5 5a2 2 0 0 1 3.008-1.728l11.997 6.998a2 2 0 0 1 .003 3.458l-12 7A2 2 0 0 1 5 19z",key:"10ikf1"}]]};u.node;const p=c(u);/**
 * @license lucide-react v1.63.0 - ISC
 *
 * This source code is licensed under the ISC license.
 * See the LICENSE file in the root directory of this source tree.
 */const l={name:"rocket",size:24,node:[["path",{d:"M12 15v5s3.03-.55 4-2c1.08-1.62 0-5 0-5",key:"qeys4"}],["path",{d:"M4.5 16.5c-1.5 1.26-2 5-2 5s3.74-.5 5-2c.71-.84.7-2.13-.09-2.91a2.18 2.18 0 0 0-2.91-.09",key:"u4xsad"}],["path",{d:"M9 12a22 22 0 0 1 2-3.95A12.88 12.88 0 0 1 22 2c0 2.72-.78 7.5-6 11a22.4 22.4 0 0 1-4 2z",key:"676m9"}],["path",{d:"M9 12H4s.55-3.03 2-4c1.62-1.08 5 .05 5 .05",key:"92ym6u"}]]};l.node;const x=c(l),D={RASCUNHO:"Rascunho",AGENDADO:"Agendado",EM_VENDAS:"Em vendas",PAUSADO:"Pausado",ESGOTADO:"Esgotado",ENCERRADO:"Encerrado",CANCELADO:"Cancelado"};function O({status:a}){const n=a==="EM_VENDAS"?"ok":a==="PAUSADO"?"warn":a==="ESGOTADO"?"brand":"neutral";return s.jsxs(A,{tone:n,children:["● ",D[a]]})}function f({id:a,status:n,size:o="sm"}){const t=h(),e=g({mutationFn:r=>y.organizer[r](a),onSuccess:()=>{t.invalidateQueries({queryKey:["organizer"]}),t.invalidateQueries({queryKey:["events"]})},onError:r=>window.alert(E(r))});return n==="RASCUNHO"||n==="AGENDADO"?s.jsxs(i,{size:o,loading:e.isPending,onClick:()=>e.mutate("publish"),children:[s.jsx(x,{className:"size-4","aria-hidden":!0})," Publicar"]}):n==="EM_VENDAS"?s.jsxs(i,{size:o,variant:"danger",loading:e.isPending,onClick:()=>{window.confirm("Pausar as vendas agora? Novas reservas ficam bloqueadas.")&&e.mutate("pause")},children:[s.jsx(k,{className:"size-4","aria-hidden":!0})," Pausar vendas"]}):n==="PAUSADO"?s.jsxs(i,{size:o,variant:"success",loading:e.isPending,onClick:()=>e.mutate("resume"),children:[s.jsx(p,{className:"size-4","aria-hidden":!0})," Retomar vendas"]}):null}function j(a,n){const o=n.map(r=>r.map(m=>`"${String(m).replace(/"/g,'""')}"`).join(";")).join(`
`),t=URL.createObjectURL(new Blob(["\uFEFF"+o],{type:"text/csv;charset=utf-8"})),e=document.createElement("a");e.href=t,e.download=a,e.click(),URL.revokeObjectURL(t)}export{O as S,D as a,f as b,j as d};
