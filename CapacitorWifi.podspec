
  Pod::Spec.new do |s|
    s.name = 'CapacitorWifi'
    s.version = '0.0.1'
    s.summary = 'Capacitor Wifi Plugin'
    s.license = 'MIT'
    s.homepage = 'digaus/capacitor-wifi'
    s.author = 'Dirk Gausmann'
    s.source = { :git => 'digaus/capacitor-wifi', :tag => s.version.to_s }
    s.source_files = 'ios/Plugin/**/*.{swift,h,m,c,cc,mm,cpp}'
    s.ios.deployment_target  = '11.0'
    s.dependency 'Capacitor'
  end