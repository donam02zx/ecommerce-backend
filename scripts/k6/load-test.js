import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');

// Configuration
export const options = {
    stages: [
        { duration: '30s', target: 10 },  // Ramp up to 10 users
        { duration: '1m', target: 10 },   // Stay at 10 users
        { duration: '30s', target: 0 },   // Ramp down to 0
    ],
    thresholds: {
        http_req_duration: ['p(95)<2000'], // 95% requests < 2s
        errors: ['rate<0.1'],              // Error rate < 10%
    },
};

const BASE_URL = 'http://localhost:8081';

export default function () {
    // Test 1: GET /api/products/top
    const topProductsRes = http.get(`${BASE_URL}/api/products/top`);

    check(topProductsRes, {
        'top-products status is 200': (r) => r.status === 200,
        'top-products has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.success === true && Array.isArray(body.data);
            } catch {
                return false;
            }
        },
    });

    // Record error if status is not 200
    errorRate.add(topProductsRes.status !== 200);

    // Test 2: GET /api/products (search/filter)
    const productsRes = http.get(`${BASE_URL}/api/products?page=1&limit=20`);

    check(productsRes, {
        'products status is 200': (r) => r.status === 200,
        'products has data': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.success === true && body.data?.content?.length > 0;
            } catch {
                return false;
            }
        },
    });

    errorRate.add(productsRes.status !== 200);

    // Random wait between requests
    sleep(Math.random() * 2 + 1);
}