declare module "@capacitor/core" {
  interface PluginRegistry {
    Wifi: WifiPlugin;
  }
}

export interface WifiPlugin {
  scan(): Promise<{}>;
  connect(options: { ssid: string, password?: string, authType?: string }): Promise<{ssid: string}>;
  connectPrefix(options: { ssid: string, password?: string }): Promise<{ssid: string}>;
  getWifiIP(): Promise<{ip: string}>;
  getConnectedSSID(): Promise<{ssid: string}>;
}
