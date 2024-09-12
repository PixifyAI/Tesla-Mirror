import React, { useState, useEffect, useRef } from 'react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';

const PhoneMirror = () => {
  const [isConnected, setIsConnected] = useState(false);
  const [token, setToken] = useState('');
  const [error, setError] = useState(null);
  const wsRef = useRef(null);
  const canvasRef = useRef(null);
  const [screenMetadata, setScreenMetadata] = useState(null);

  useEffect(() => {
    return () => {
      if (wsRef.current) {
        wsRef.current.close();
      }
    };
  }, []);

  const connectToWebSocket = () => {
    wsRef.current = new WebSocket(`ws://your-server-url:3001?token=${token}`);

    wsRef.current.onopen = () => {
      console.log('WebSocket Connected');
      setIsConnected(true);
      setError(null);
    };

    wsRef.current.onmessage = (event) => {
      const data = JSON.parse(event.data);
      if (data.type === 'screenUpdate') {
        updateScreen(data);
      }
    };

    wsRef.current.onclose = (event) => {
      console.log('WebSocket Disconnected', event.reason);
      setIsConnected(false);
      setError('Connection closed. Trying to reconnect...');
      setTimeout(connectToWebSocket, 5000);
    };

    wsRef.current.onerror = (error) => {
      console.error('WebSocket Error', error);
      setError('An error occurred. Please try again.');
    };
  };

  const updateScreen = (data) => {
    setScreenMetadata(data.metadata);
    const canvas = canvasRef.current;
    const ctx = canvas.getContext('2d');
    const img = new Image();
    img.onload = () => {
      canvas.width = data.metadata.width;
      canvas.height = data.metadata.height;
      ctx.drawImage(img, 0, 0);
    };
    img.src = 'data:image/jpeg;base64,' + data.imageData;
  };

  const handleConnect = () => {
    connectToWebSocket();
  };

  const handleDisconnect = () => {
    if (wsRef.current) {
      wsRef.current.close();
    }
    setIsConnected(false);
    setError(null);
  };

  const handleInteraction = (event) => {
    if (wsRef.current && wsRef.current.readyState === WebSocket.OPEN) {
      const canvas = canvasRef.current;
      const rect = canvas.getBoundingClientRect();
      const x = (event.clientX - rect.left) / rect.width;
      const y = (event.clientY - rect.top) / rect.height;
      
      let interaction;
      switch (event.type) {
        case 'click':
          interaction = { type: 'tap', x, y };
          break;
        case 'touchmove':
          interaction = { 
            type: 'swipe', 
            startX: x, 
            startY: y, 
            endX: (event.changedTouches[0].clientX - rect.left) / rect.width,
            endY: (event.changedTouches[0].clientY - rect.top) / rect.height
          };
          break;
        default:
          return;
      }
      
      wsRef.current.send(JSON.stringify(interaction));
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-gray-100">
      <Card className="w-full max-w-3xl p-6">
        <h2 className="text-2xl font-bold mb-4">Phone Mirror</h2>
        {error && (
          <Alert variant="destructive" className="mb-4">
            <AlertTitle>Error</AlertTitle>
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        {!isConnected && (
          <Input
            type="text"
            placeholder="Enter token"
            value={token}
            onChange={(e) => setToken(e.target.value)}
            className="mb-4"
          />
        )}
        {isConnected ? (
          <>
            <canvas
              ref={canvasRef}
              className="border border-gray-300 mb-4 w-full"
              onClick={handleInteraction}
              onTouchMove={handleInteraction}
            />
            <Button onClick={handleDisconnect} variant="destructive">Disconnect</Button>
          </>
        ) : (
          <Button onClick={handleConnect} disabled={!token}>Connect Phone</Button>
        )}
        {screenMetadata && (
          <p className="mt-4 text-sm text-gray-500">
            Screen: {screenMetadata.width}x{screenMetadata.height} ({screenMetadata.density}dpi)
          </p>
        )}
      </Card>
    </div>
  );
};

export default PhoneMirror;
