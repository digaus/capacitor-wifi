import Foundation
import Capacitor
import SystemConfiguration.CaptiveNetwork

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */
@objc(Wifi)
public class Wifi: CAPPlugin {
    
    @objc func scan(_ call: CAPPluginCall) {
        call.error("NOT_AVAILABLE_ON_IOS")
    }
    @objc func connect(_ call: CAPPluginCall) {
        guard let ssid = call.options["ssid"] as? String else {
            call.reject("Must provide an ssid")
            return
        }
        let password = call.getString("password") ?? nil

        if #available(iOS 11, *) {
            var configuration;
            if stringA? != nil {
                configuration = NEHotspotConfiguration.init(ssid: ssid, passphrase: password, isWEP: false)
            } else {
                configuration = NEHotspotConfiguration.init(ssid: ssid)
            }
            configuration.joinOnce = true

            NEHotspotConfigurationManager.shared.apply(configuration) { (error) in
                if error != nil {
                    if error?.localizedDescription == "already associated."
                    {
                        call.success([
                            "ssid": ssid
                        ])
                    }
                    else {
                        call.error("CONNECTION_FAILED")
                    }
                }
                else {
                    call.success([
                        "ssid": ssid
                    ])
                }
            }
        } else {
            call.error("ONLY_SUPPORTED_IOS_11")
        }
    }
    @objc func connectPrefix(_ call: CAPPluginCall) {
        guard let ssid = call.options["ssid"] as? String else {
            call.reject("Must provide an ssid")
            return
        }
        let password = call.getString("password") ?? nil

        if #available(iOS 13, *) {
            var configuration;

            if stringA? != nil {
                configuration = NEHotspotConfiguration.init(ssidPrefix: ssid, passphrase: password, isWEP: false)
            } else {
                configuration = NEHotspotConfiguration.init(ssidPrefix: ssid)
            }
            configuration.joinOnce = true

            NEHotspotConfigurationManager.shared.apply(configuration) { (error) in
                if error != nil {
                    if error?.localizedDescription == "already associated."
                    {
                        call.success([
                            "ssid": ssid
                        ])
                    }
                    else {
                        call.error("CONNECTION_FAILED")
                    }
                }
                else {
                    call.success([
                        "ssid": ssid
                    ])
                }
            }
        } else {
            call.error("ONLY_SUPPORTED_IOS_11")
        }
    }
   
    // Return IP address of WiFi interface (en0) as a String, or `nil`
    @objc func getWifiIP(_ call: CAPPluginCall) {
        var address : String? = nil

        // Get list of all interfaces on the local machine:
        var ifaddr : UnsafeMutablePointer<ifaddrs> = nil
        if getifaddrs(&ifaddr) == 0 {

            // For each interface ...
            var ptr = ifaddr
            while ptr != nil {
                defer { ptr = ptr.memory.ifa_next }

                let interface = ptr.memory

                // Check for IPv4 or IPv6 interface:
                let addrFamily = interface.ifa_addr.memory.sa_family
                if addrFamily == UInt8(AF_INET) || addrFamily == UInt8(AF_INET6) {

                    // Check interface name:
                    if let name = String.fromCString(interface.ifa_name) where name == "en0" {

                        // Convert interface address to a human readable string:
                        var hostname = [CChar](count: Int(NI_MAXHOST), repeatedValue: 0)
                        getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.memory.sa_len),
                                    &hostname, socklen_t(hostname.count),
                                    nil, socklen_t(0), NI_NUMERICHOST)
                        address = String.fromCString(hostname)
                    }
                }
            }
            freeifaddrs(ifaddr)
        }
        guard let myString = address, !myString.isEmpty else {
            call.error("NO_WIFI_IP_AVAILABLE");
            return
        } 
        call.success([
            "ip": address
        ])
    }

    @objc func getConnectedSSID()  {
        var ssid: String? = nil
        if let interfaces = CNCopySupportedInterfaces() as NSArray? {
            for interface in interfaces {
                if let interfaceInfo = CNCopyCurrentNetworkInfo(interface as! CFString) as NSDictionary? {
                    ssid = interfaceInfo[kCNNetworkInfoKeySSID as String] as? String
                    break
                }
            }
        }
        guard let myString = ssid, !myString.isEmpty else {
            call.error("WIFI_INFORMATION_EMPTY");
            return
        } 
        call.success([
            "ssid": ssid
        ])
    }
}
