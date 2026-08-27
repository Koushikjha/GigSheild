import { client } from './client'

// POST /api/v1/payments/order/{policyId} -> CreateOrderResponse { orderId, amountInr, currency }
// Backend's PaymentService.createPremiumOrder() reads the premium off the
// policy itself, so nothing but the id is required here.
export function createOrder(policyId) {
  return client.post(`/api/v1/payments/order/${policyId}`).then((response) => response.data)
}
