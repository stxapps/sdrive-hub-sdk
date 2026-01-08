require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'RNBlockstackSDK'
  s.version        = package['version']
  s.summary        = package['description']
  s.license        = package['license']
  s.authors        = { 'STX Apps' => 'stxapps.com' }
  s.homepage       = 'https://github.com/stxapps/sdrive-hub-sdk'
  s.platforms      = { :ios => '15.1' }
  s.swift_version  = '5.9'
  s.source         = { git: 'https://github.com/stxapps/sdrive-hub-sdk.git', :branch => 'main' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'Blockstack'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
