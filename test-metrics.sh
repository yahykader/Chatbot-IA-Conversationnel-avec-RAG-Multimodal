#!/bin/bash

echo "🧪 Test RAG Metrics Stack"
echo ""

# 1. Test Actuator
echo "1️⃣ Test Spring Boot Actuator..."
ACTUATOR=$(curl -s http://localhost:8090/actuator/prometheus | wc -l)
echo "   📊 Métriques totales: $ACTUATOR lignes"

RAG_METRICS=$(curl -s http://localhost:8090/actuator/prometheus | grep "rag_" | wc -l)
echo "   📊 Métriques RAG: $RAG_METRICS métriques"

if [ $RAG_METRICS -eq 0 ]; then
    echo "   ⚠️  Aucune métrique RAG trouvée"
    echo "   → Ingester un fichier pour générer des métriques"
fi

echo ""

# 2. Test Prometheus
echo "2️⃣ Test Prometheus Target..."
curl -s http://localhost:9090/api/v1/targets | grep -q "spring-boot-rag"
if [ $? -eq 0 ]; then
    echo "   ✅ Target spring-boot-rag trouvé"
else
    echo "   ❌ Target spring-boot-rag PAS trouvé"
    echo "   → Vérifier prometheus.yml"
fi

echo ""

# 3. Test Query Prometheus
echo "3️⃣ Test Query Prometheus..."
UP=$(curl -s "http://localhost:9090/api/v1/query?query=up{job=\"spring-boot-rag\"}" | grep -o '"value":\[.*,"\(0\|1\)"\]' | grep -o '[01]"' | tr -d '"')
if [ "$UP" = "1" ]; then
    echo "   ✅ Application UP dans Prometheus"
else
    echo "   ❌ Application DOWN dans Prometheus"
fi

echo ""
echo "🎯 URLs de vérification:"
echo "   Prometheus Targets: http://localhost:9090/targets"
echo "   Prometheus Graph: http://localhost:9090/graph"
echo "   Grafana: http://localhost:3000"
echo "   Actuator: http://localhost:8090/actuator/prometheus"