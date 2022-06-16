import Foundation

@objc public class GooglePay: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}
