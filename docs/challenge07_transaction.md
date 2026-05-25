# Challenge 7: Transaction Flow

## Transaction Flow trong createOrder()

Toàn bộ flow nằm trong 1 method duy nhất được đánh dấu `@Transactional`.
Nếu bất kỳ bước nào throw exception → Spring tự động rollback toàn bộ.

```
STEP 1: Validate user
        ↓ fail → rollback (không có gì để rollback)
STEP 2: Validate product (exists + active)
        ↓ fail → rollback
STEP 3: Check inventory (available >= requested)
        ↓ fail → rollback
STEP 4: Reserve stock
        - UPDATE inventory.reserved_quantity += qty
        - INSERT stock_transactions (type = RESERVE)
        ↓ fail → rollback: reserved_quantity và stock_transaction bị hủy
STEP 5: Create order
        - INSERT orders
        ↓ fail → rollback: reserved_quantity, stock_transaction, order bị hủy
STEP 6: Create order items
        - INSERT order_items (lưu price tại thời điểm mua)
        ↓ fail → rollback: toàn bộ bị hủy
STEP 7: Clear cart
        - DELETE cart_items
        ↓ fail → rollback: toàn bộ bị hủy
```

## Rollback Case Demo

### Case 1: Product không tồn tại
```json
POST /api/orders
{ "items": [{ "productId": 9999, "quantity": 1 }] }
```
- Fail ở STEP 2
- Rollback: không có gì bị thay đổi trong DB

### Case 2: Không đủ tồn kho
```json
POST /api/orders
{ "items": [{ "productId": 1, "quantity": 999 }] }
```
- Fail ở STEP 3
- Rollback: reserved_quantity không bị tăng

### Case 3: Order có nhiều item, item thứ 2 hết hàng
```json
POST /api/orders
{
  "items": [
    { "productId": 1, "quantity": 1 },
    { "productId": 2, "quantity": 999 }
  ]
}
```
- STEP 4 đã reserve stock cho productId=1
- Fail ở STEP 3 khi check productId=2
- Rollback: reserved_quantity của productId=1 bị hoàn lại, không có order nào được tạo

## Tại sao @Transactional hoạt động?

Spring sử dụng AOP (Aspect Oriented Programming) để wrap method trong 1 transaction:

```
BEGIN TRANSACTION
    try {
        createOrder(...)  // toàn bộ logic
        COMMIT
    } catch (RuntimeException e) {
        ROLLBACK
        throw e
    }
```

Chỉ cần throw `RuntimeException` (hoặc subclass của nó) là Spring tự rollback.
`AppException` extends `RuntimeException` nên mọi throw trong code đều trigger rollback.
