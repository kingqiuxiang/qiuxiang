import React, { useState, useEffect } from 'react';
import { Plus, Trash2, AlertCircle } from 'lucide-react';

function KeyValueEditor({ rows, onChange, keyPlaceholder = 'key', valuePlaceholder = 'value' }) {
  const update = (i, field, value) => {
    const next = rows.map((r, idx) => (idx === i ? { ...r, [field]: value } : r));
    onChange(next);
  };
  const add = () => onChange([...rows, { key: '', value: '' }]);
  const remove = (i) => onChange(rows.filter((_, idx) => idx !== i));

  return (
    <div className="space-y-2">
      {rows.length === 0 && <p className="text-xs text-slate-500">无参数</p>}
      {rows.map((row, i) => (
        <div key={i} className="flex items-center gap-2">
          <input className="input flex-1 font-mono text-xs" placeholder={keyPlaceholder} value={row.key} onChange={(e) => update(i, 'key', e.target.value)} />
          <span className="text-slate-600">:</span>
          <input className="input flex-[1.5] font-mono text-xs" placeholder={valuePlaceholder} value={row.value} onChange={(e) => update(i, 'value', e.target.value)} />
          <button className="rounded-lg p-1.5 text-slate-500 hover:bg-rose-500/15 hover:text-rose-300" onClick={() => remove(i)}>
            <Trash2 size={14} />
          </button>
        </div>
      ))}
      <button className="btn-ghost w-full !py-1.5 text-xs" onClick={add}>
        <Plus size={14} /> 添加
      </button>
    </div>
  );
}

const objToRows = (obj) => Object.entries(obj || {}).map(([key, value]) => ({ key, value: typeof value === 'string' ? value : JSON.stringify(value) }));
const rowsToObj = (rows) => {
  const out = {};
  for (const { key, value } of rows) {
    if (!key) continue;
    let v = value;
    if (/^(true|false|null|-?\d+(\.\d+)?)$/.test(value)) { try { v = JSON.parse(value); } catch { /* keep */ } }
    out[key] = v;
  }
  return out;
};

export default function RequestEditor({ filled, onChange }) {
  const [bodyText, setBodyText] = useState('');
  const [bodyErr, setBodyErr] = useState('');

  useEffect(() => {
    setBodyText(filled.body == null ? '' : JSON.stringify(filled.body, null, 2));
    setBodyErr('');
  }, [filled.body]);

  const setSection = (section, rows) => onChange({ ...filled, [section]: rowsToObj(rows) });

  const onBodyChange = (text) => {
    setBodyText(text);
    if (!text.trim()) { setBodyErr(''); onChange({ ...filled, body: null }); return; }
    try { const parsed = JSON.parse(text); setBodyErr(''); onChange({ ...filled, body: parsed }); }
    catch (e) { setBodyErr(e.message); }
  };

  return (
    <div className="space-y-5">
      <Group title="Path 参数"><KeyValueEditor rows={objToRows(filled.pathParams)} onChange={(r) => setSection('pathParams', r)} /></Group>
      <Group title="Query 参数"><KeyValueEditor rows={objToRows(filled.query)} onChange={(r) => setSection('query', r)} /></Group>
      <Group title="Headers"><KeyValueEditor rows={objToRows(filled.headers)} onChange={(r) => setSection('headers', r)} /></Group>
      <Group title="Body (JSON)">
        <textarea
          className="input h-48 resize-y font-mono text-xs leading-relaxed code-scroll"
          value={bodyText}
          onChange={(e) => onBodyChange(e.target.value)}
          placeholder="{ }"
          spellCheck={false}
        />
        {bodyErr && (
          <p className="mt-1 flex items-center gap-1 text-[11px] text-rose-400">
            <AlertCircle size={12} /> JSON 解析错误：{bodyErr}
          </p>
        )}
      </Group>
    </div>
  );
}

function Group({ title, children }) {
  return (
    <div>
      <p className="label mb-2">{title}</p>
      {children}
    </div>
  );
}
