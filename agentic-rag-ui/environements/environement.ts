// environments/environment.ts
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8090',
  
  // ✅ WebSocket natif (RECOMMANDÉ)
  wsUrl: 'ws://localhost:8090',
  
  // ✅ Endpoints spécifiques
  wsProgressEndpoint: '/ws',              // STOMP
  wsAssistantEndpoint: '/ws/assistant'    // Raw WebSocket
};

