import { WebPlugin } from '@capacitor/core';
import { WifiPlugin } from './definitions';

export class WifiWeb extends WebPlugin implements WifiPlugin {
  constructor() {
    super({
      name: 'Wifi',
      platforms: ['web']
    });
  }

  async scan(): Promise<{value: string}> {
      return null;
  }
  async connect(options: { ssid: string, password?: string, authType?: string }): Promise<{ssid: string}> {
    return {ssid: null};
  }
  async connectPrefix(options: { ssid: string, password?: string}): Promise<{ssid: string}> {
    return {ssid: null};
  }
  async getConnectedSSID(): Promise<{ssid: string}> {
    return { ssid: null }
  }
  async getWifiIP(): Promise<{ip: string}> {
    return { ip: null };
  }
}

const Wifi = new WifiWeb();

export { Wifi };

import { registerWebPlugin } from '@capacitor/core';
registerWebPlugin(Wifi);
