import Foundation
import Capacitor
import SystemConfiguration.CaptiveNetwork
import NetworkExtension
import CoreLocation
/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitor.ionicframework.com/docs/plugins/ios
 */

class WifiHandler: NSObject, CLLocationManagerDelegate {
    var locManager : CLLocationManager!
    var call: CAPPluginCall

    init(call: CAPPluginCall) {
        self.call = call
        super.init()
        checkSSID()
        
    }
    @objc func checkSSID() {
        if #available(iOS 13.0, *) {
             let enabled = CLLocationManager.locationServicesEnabled()
             let status = CLLocationManager.authorizationStatus()

             if status == .denied {
                 call.error("LOCATION_DENIED");
             }
             
             if status == .notDetermined || !enabled {
                if  locManager == nil {
                    requestPermission()
                }
                // TODO temp fix

                usleep(500)
                checkSSID()

             }
            
             if status == .authorizedWhenInUse {
                getConnectedSSID()
             }
        } else {
            getConnectedSSID()
        }
    }

    @objc func requestPermission() {
        locManager = CLLocationManager()
        locManager.delegate = self
        locManager.requestWhenInUseAuthorization()
    }
    
    // TODO not being called
    @objc func locManager(manager: CLLocationManager, didChangeAuthorizationStatus status: CLAuthorizationStatus) {
        switch status {
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
            break
        case .authorizedWhenInUse:
            getConnectedSSID()
            break
        case .authorizedAlways:
            getConnectedSSID()
            break
        case .restricted:
            call.error("LOCATION_DENIED");
            break
        case .denied:
            call.error("LOCATION_DENIED");
            break
        }
    }
    @objc func getConnectedSSID() {
        var ssid: String?

        if let interfaces = CNCopySupportedInterfaces() as NSArray? {
            for interface in interfaces {
                if let interfaceInfo = CNCopyCurrentNetworkInfo(interface as! CFString) as NSDictionary? {
                    ssid = interfaceInfo[kCNNetworkInfoKeySSID as String] as? String
                    break
                }
            }
        }

        guard let myString = ssid, !myString.isEmpty else {
            self.call.error("WIFI_INFORMATION_EMPTY");
           return
        }
        self.call.success([
           "ssid": ssid!
        ])
    }
}
@objc(Wifi)
public class Wifi: CAPPlugin {
    
    var wifiHandler: WifiHandler?

    @objc func scan(_ call: CAPPluginCall) {
        call.error("NOT_AVAILABLE_ON_IOS")
    }
    @objc func connect(_ call: CAPPluginCall) {
        guard let ssid = call.options["ssid"] as? String else {
            call.reject("Must provide an ssid")
            return
        }
        let password : String? = call.getString("password") ?? nil

        if #available(iOS 11, *) {
            var configuration : NEHotspotConfiguration
            if password != nil {
                configuration = NEHotspotConfiguration.init(ssid: ssid, passphrase: password!, isWEP: false)
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
        let password : String? = call.getString("password") ?? nil

        if #available(iOS 13, *) {
            var configuration : NEHotspotConfiguration
            if password != nil {
                configuration = NEHotspotConfiguration.init(ssidPrefix: ssid, passphrase: password!, isWEP: false)
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
        let address = getWiFiAddress()
        guard let myString = address, !myString.isEmpty else {
            call.error("NO_WIFI_IP_AVAILABLE");
            return
        } 
        call.success([
            "ip": address!
        ])
    }

    @objc func getConnectedSSID(_ call: CAPPluginCall)  {
        DispatchQueue.main.async {
            self.wifiHandler = WifiHandler(call: call)
        }
    }
    @objc func getWiFiAddress() -> String? {
        var address : String?

        // Get list of all interfaces on the local machine:
        var ifaddr : UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0 else { return nil }
        guard let firstAddr = ifaddr else { return nil }

        // For each interface ...
        for ifptr in sequence(first: firstAddr, next: { $0.pointee.ifa_next }) {
            let interface = ifptr.pointee

            // Check for IPv4 or IPv6 interface:
            let addrFamily = interface.ifa_addr.pointee.sa_family
            if addrFamily == UInt8(AF_INET) || addrFamily == UInt8(AF_INET6) {

                // Check interface name:
                let name = String(cString: interface.ifa_name)
                if  name == "en0" {

                    // Convert interface address to a human readable string:
                    var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
                    getnameinfo(interface.ifa_addr, socklen_t(interface.ifa_addr.pointee.sa_len),
                                &hostname, socklen_t(hostname.count),
                                nil, socklen_t(0), NI_NUMERICHOST)
                    address = String(cString: hostname)
                }
            }
        }
        freeifaddrs(ifaddr)

        return address
    }
}
