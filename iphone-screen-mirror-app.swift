import UIKit
import ReplayKit
import WebKit

class ScreenMirrorViewController: UIViewController, RPScreenRecorderDelegate {
    private var webSocket: URLSessionWebSocketTask?
    private let recorder = RPScreenRecorder.shared()
    private var isRecording = false
    private var timer: Timer?
    
    private lazy var startStopButton: UIButton = {
        let button = UIButton(type: .system)
        button.setTitle("Start Mirroring", for: .normal)
        button.addTarget(self, action: #selector(toggleRecording), for: .touchUpInside)
        return button
    }()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .white
        setupUI()
        connectWebSocket()
    }
    
    private func setupUI() {
        view.addSubview(startStopButton)
        startStopButton.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            startStopButton.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            startStopButton.centerYAnchor.constraint(equalTo: view.centerYAnchor)
        ])
    }
    
    private func connectWebSocket() {
        guard let url = URL(string: "ws://your-server-url:3001") else { return }
        let session = URLSession(configuration: .default)
        webSocket = session.webSocketTask(with: url)
        webSocket?.resume()
    }
    
    @objc private func toggleRecording() {
        if isRecording {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    private func startRecording() {
        guard !isRecording else { return }
        
        recorder.isMicrophoneEnabled = false
        recorder.startCapture { (sampleBuffer, bufferType, error) in
            if let error = error {
                print("Error starting capture: \(error.localizedDescription)")
                return
            }
            
            if bufferType == .video {
                self.processVideoFrame(sampleBuffer)
            }
        } completionHandler: { error in
            if let error = error {
                print("Error starting screen recording: \(error.localizedDescription)")
            } else {
                DispatchQueue.main.async {
                    self.isRecording = true
                    self.startStopButton.setTitle("Stop Mirroring", for: .normal)
                }
            }
        }
    }
    
    private func stopRecording() {
        guard isRecording else { return }
        
        recorder.stopCapture { error in
            if let error = error {
                print("Error stopping screen recording: \(error.localizedDescription)")
            } else {
                DispatchQueue.main.async {
                    self.isRecording = false
                    self.startStopButton.setTitle("Start Mirroring", for: .normal)
                }
            }
        }
    }
    
    private func processVideoFrame(_ sampleBuffer: CMSampleBuffer) {
        guard let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let ciImage = CIImage(cvImageBuffer: imageBuffer)
        let context = CIContext()
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else { return }
        
        let image = UIImage(cgImage: cgImage)
        guard let jpegData = image.jpegData(compressionQuality: 0.5) else { return }
        
        let base64String = jpegData.base64EncodedString()
        
        let screenMetadata = ScreenMetadata(width: Int(image.size.width), height: Int(image.size.height), density: Int(UIScreen.main.scale * 160))
        let payload = ScreenPayload(type: "screenUpdate", metadata: screenMetadata, imageData: base64String)
        
        sendWebSocketMessage(payload)
    }
    
    private func sendWebSocketMessage(_ payload: ScreenPayload) {
        do {
            let jsonData = try JSONEncoder().encode(payload)
            if let jsonString = String(data: jsonData, encoding: .utf8) {
                webSocket?.send(.string(jsonString)) { error in
                    if let error = error {
                        print("Error sending WebSocket message: \(error.localizedDescription)")
                    }
                }
            }
        } catch {
            print("Error encoding payload: \(error.localizedDescription)")
        }
    }
}

struct ScreenMetadata: Codable {
    let width: Int
    let height: Int
    let density: Int
}

struct ScreenPayload: Codable {
    let type: String
    let metadata: ScreenMetadata
    let imageData: String
}
