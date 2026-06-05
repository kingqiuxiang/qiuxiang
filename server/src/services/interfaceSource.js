import * as yapi from './yapiService.js';
import * as sample from './sampleData.js';

/** Unified access to interfaces: real YAPI when configured, else built-in sample. */
export async function getMenu() {
  if (yapi.isConfigured()) {
    const menu = await yapi.listMenu();
    return { source: 'yapi', menu };
  }
  return { source: 'sample', menu: sample.listMenu() };
}

export async function getInterfaceById(id, catName = '') {
  if (yapi.isConfigured()) {
    const iface = await yapi.getInterface(id, catName);
    return { source: 'yapi', interface: iface };
  }
  return { source: 'sample', interface: sample.getInterface(id) };
}
