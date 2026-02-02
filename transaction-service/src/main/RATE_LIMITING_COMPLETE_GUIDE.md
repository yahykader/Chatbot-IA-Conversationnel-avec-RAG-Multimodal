# 🚦 RATE LIMITING - GUIDE COMPLET D'IMPLÉMENTATION

## 📦 DÉPENDANCES MAVEN

Ajoutez ces dépendances dans votre `pom.xml` :

```xml
<!-- Bucket4j (Rate Limiting) -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.7.0</version>
</dependency>

<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis-lettuce</artifactId>
    <version>8.7.0</version>
</dependency>

<!-- Lettuce (Redis Client) -->
<dependency>
    <groupId>io.lettuce</groupId>
    <artifactId>lettuce-core</artifactId>
    <version>6.3.0.RELEASE</version>
</dependency>
```

---

## 📝 CONFIGURATION (application.yml)

```yaml
# ============================================================================
# RATE LIMITING CONFIGURATION
# ============================================================================

# Redis (nécessaire pour Bucket4j distribué)
redis:
  host: localhost
  port: 6379

# Limites par endpoint (requêtes par minute)
rate-limit:
  upload:
    requests-per-minute: 10      # Upload fichier unique
  batch:
    requests-per-minute: 5       # Upload batch
  delete:
    requests-per-minute: 20      # Suppression
  search:
    requests-per-minute: 50      # Recherche
  default:
    requests-per-minute: 30      # Autres endpoints
```

---

## 📁 STRUCTURE DES FICHIERS

```
src/main/java/com/exemple/transactionservice/
├── config/
│   ├── RateLimitConfig.java        ✨ NOUVEAU (Config Bucket4j)
│   └── WebMvcConfig.java           ✨ NOUVEAU (Enregistrement intercepteur)
├── interceptor/
│   └── RateLimitInterceptor.java   ✨ NOUVEAU (Intercepteur HTTP)
└── service/
    └── ratelimit/
        └── RateLimitService.java   ✨ NOUVEAU (Logique rate limiting)
```

---

## 🎯 FONCTIONNEMENT

### **Architecture :**

```
Request HTTP
    ↓
RateLimitInterceptor
    ↓
RateLimitService
    ↓
Bucket4j + Redis
    ↓
[Autorisé] → Controller
[Bloqué]   → 429 Too Many Requests
```

### **Algorithme Token Bucket :**

```
┌─────────────────────────────────┐
│ Bucket (ex: 10 tokens)          │
│ ●●●●●●●●●●                      │
└─────────────────────────────────┘
         ↓
    Requête arrive
         ↓
  Consomme 1 token
         ↓
┌─────────────────────────────────┐
│ Bucket (9 tokens restants)      │
│ ●●●●●●●●●○                      │
└─────────────────────────────────┘
         ↓
    Après 1 minute
         ↓
┌─────────────────────────────────┐
│ Bucket (rechargé à 10)          │
│ ●●●●●●●●●●                      │
└─────────────────────────────────┘
```

---

## 🔍 DÉTECTION AUTOMATIQUE DES ENDPOINTS

L'intercepteur détecte automatiquement le type d'endpoint :

| Endpoint Pattern | Limite Appliquée |
|-----------------|------------------|
| `/upload/batch*` | `batchLimit` (5/min) |
| `/upload*` | `uploadLimit` (10/min) |
| `/file/*` (DELETE) | `deleteLimit` (20/min) |
| `/search*` | `searchLimit` (50/min) |
| Autres | `defaultLimit` (30/min) |

**Exclusions (pas de rate limit) :**
- `/health`
- `/health/detailed`
- `/strategies`

---

## 👤 IDENTIFICATION UTILISATEUR

### **Priorité (ordre) :**

1. **Header `X-User-Id`** (recommandé)
   ```bash
   curl -H "X-User-Id: user123" http://localhost:8080/api/v1/ingestion/upload
   ```

2. **JWT Token** (si authentification activée)
   ```bash
   curl -H "Authorization: Bearer eyJhbG..." http://localhost:8080/api/v1/ingestion/upload
   ```

3. **Session utilisateur** (si login activé)

4. **IP client** (fallback)
   - Automatique si aucune des options ci-dessus

---

## 📊 HEADERS HTTP

### **En cas de succès (200 OK) :**

```http
HTTP/1.1 200 OK
X-RateLimit-Remaining: 7
Content-Type: application/json
```

### **En cas de dépassement (429 Too Many Requests) :**

```http
HTTP/1.1 429 Too Many Requests
X-RateLimit-Limit: voir configuration
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1706634120
Retry-After: 45
Content-Type: application/json

{
  "error": "Too Many Requests",
  "message": "Rate limit dépassé. Réessayez dans 45 secondes.",
  "retryAfterSeconds": 45,
  "timestamp": 1706634075000
}
```

---

## 🧪 TESTS

### **Test 1 : Vérifier rate limiting upload**

```bash
# Envoyer 15 requêtes rapidement (limite = 10)
for i in {1..15}; do
  echo "Request $i:"
  curl -X POST "http://localhost:8080/api/v1/ingestion/upload" \
    -H "X-User-Id: test-user" \
    -F "file=@test.pdf" \
    -w "\nHTTP Status: %{http_code}\n\n"
  sleep 0.5
done

# Résultat attendu :
# Requêtes 1-10: 200 OK
# Requêtes 11-15: 429 Too Many Requests
```

### **Test 2 : Vérifier headers**

```bash
curl -v -X POST "http://localhost:8080/api/v1/ingestion/upload" \
  -H "X-User-Id: test-user" \
  -F "file=@test.pdf"

# Vérifier dans la réponse :
# < X-RateLimit-Remaining: 9
```

### **Test 3 : Différents utilisateurs**

```bash
# User 1
curl -H "X-User-Id: user1" http://localhost:8080/api/v1/ingestion/stats

# User 2
curl -H "X-User-Id: user2" http://localhost:8080/api/v1/ingestion/stats

# Chaque utilisateur a son propre bucket (limites indépendantes)
```

---

## ⚙️ CONFIGURATION AVANCÉE

### **Configuration par environnement :**

**application-dev.yml** (développement - plus permissif)
```yaml
rate-limit:
  upload:
    requests-per-minute: 100
  batch:
    requests-per-minute: 50
  delete:
    requests-per-minute: 200
```

**application-prod.yml** (production - strict)
```yaml
rate-limit:
  upload:
    requests-per-minute: 5
  batch:
    requests-per-minute: 2
  delete:
    requests-per-minute: 10
```

### **Limites multiples (raffinement) :**

```java
// Dans RateLimitConfig.java
@Bean
public Supplier<BucketConfiguration> uploadBucketConfig() {
    return () -> BucketConfiguration.builder()
        // 10 requêtes par minute
        .addLimit(Bandwidth.simple(10, Duration.ofMinutes(1)))
        // ET max 50 requêtes par heure
        .addLimit(Bandwidth.simple(50, Duration.ofHours(1)))
        .build();
}
```

### **Burst capacity (pics de trafic) :**

```java
@Bean
public Supplier<BucketConfiguration> uploadBucketConfig() {
    return () -> BucketConfiguration.builder()
        // Capacité: 20 tokens
        // Refill: 10 tokens par minute
        .addLimit(Bandwidth.classic(20, Refill.intervally(10, Duration.ofMinutes(1))))
        .build();
}
```

---

## 🔐 INTÉGRATION AVEC SÉCURITÉ

### **Si vous avez Spring Security :**

```java
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private String getUserId(HttpServletRequest request) {
        // Récupérer depuis Spring Security
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();  // Username
        }
        
        // Fallback
        return getClientIP(request);
    }
}
```

---

## 📊 MONITORING

### **Logs produits :**

```
2026-01-30 12:00:00 DEBUG RateLimitService : ✅ [RateLimit] OK - user=user123, endpoint=upload, remaining=7
2026-01-30 12:00:05 WARN  RateLimitService : ⚠️ [RateLimit] BLOQUÉ - user=user123, endpoint=upload, retry_in=55s
```

### **Métriques à surveiller (à implémenter) :**

- Nombre de requêtes bloquées (429)
- Nombre de requêtes autorisées
- Distribution par endpoint
- Distribution par utilisateur

---

## 🐛 TROUBLESHOOTING

### **Problème 1 : Tous les utilisateurs partagent la même limite**

**Cause :** userId est null, tous utilisent l'IP.

**Solution :**
- Vérifier le header `X-User-Id`
- Ou implémenter l'extraction depuis JWT

---

### **Problème 2 : Redis connexion refused**

**Cause :** Redis n'est pas démarré.

**Solution :**
```bash
# Démarrer Redis
docker run -d -p 6379:6379 redis:7-alpine

# Ou avec docker-compose
docker-compose up -d redis
```

---

### **Problème 3 : Rate limiting ne s'applique pas**

**Cause :** Intercepteur non enregistré ou path pattern incorrect.

**Solution :**
- Vérifier logs au démarrage : "RateLimitInterceptor enregistré"
- Vérifier path dans `WebMvcConfig`

---

## ✅ CHECKLIST IMPLÉMENTATION

### **Dépendances :**
- [ ] Ajouter `bucket4j-core` dans pom.xml
- [ ] Ajouter `bucket4j-redis-lettuce` dans pom.xml
- [ ] Ajouter `lettuce-core` dans pom.xml

### **Configuration :**
- [ ] Créer `RateLimitConfig.java`
- [ ] Créer `RateLimitService.java`
- [ ] Créer `RateLimitInterceptor.java`
- [ ] Créer `WebMvcConfig.java`
- [ ] Ajouter config dans `application.yml`

### **Infrastructure :**
- [ ] Démarrer Redis (localhost:6379)
- [ ] Vérifier connexion Redis

### **Tests :**
- [ ] Tester limite upload
- [ ] Tester limite batch
- [ ] Tester limite delete
- [ ] Vérifier headers HTTP
- [ ] Tester avec différents utilisateurs

### **Production :**
- [ ] Ajuster limites pour production
- [ ] Configurer Redis cluster (haute dispo)
- [ ] Ajouter monitoring/alerting
- [ ] Documenter pour l'équipe

---

## 🚀 AVANTAGES

✅ **Protection API** - Évite les abus
✅ **Distribué** - Fonctionne avec plusieurs instances
✅ **Flexible** - Limites configurables par endpoint
✅ **Automatique** - Appliqué par intercepteur
✅ **Standards HTTP** - Headers standard rate limiting
✅ **Fail-open** - En cas d'erreur, autorise la requête

---

## 📚 RÉFÉRENCES

- **Bucket4j Documentation:** https://bucket4j.com/
- **Token Bucket Algorithm:** https://en.wikipedia.org/wiki/Token_bucket
- **RFC 6585 (429 Status):** https://tools.ietf.org/html/rfc6585

---

**Rate Limiting prêt pour production ! 🎯**
