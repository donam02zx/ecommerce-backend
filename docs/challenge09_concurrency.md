# Challenge 9: Concurrency - No Oversell

## Vấn đề

Khi 50 request cùng lúc đặt Product A (stock=10), nếu không xử lý đúng:
- Request 1 đọc: available=10 → đặt được
- Request 2 đọc: available=10 → đặt được (chưa thấy request 1 đã reserve)
- Kết quả: 50 order thành công → **oversell 40 sản phẩm**

## Giải pháp: Optimistic Locking

### Cơ chế

`InventoryEntity` có field `@Version`:

```java
@Version
@Column(nullable = false)
private Long version;
```

Khi Hibernate update inventory, nó tự thêm điều kiện vào câu SQL:

```sql
UPDATE inventory 
SET reserved_quantity = ?, version = version + 1
WHERE id = ? AND version = ?   -- ← kiểm tra version
```

Nếu 2 request cùng đọc `version=5`:
- Request 1 update thành công → version thành 6
- Request 2 update với `WHERE version=5` → **không tìm thấy row** → `OptimisticLockingFailureException`

### Flow xử lý trong code

```
Request A và Request B cùng lúc đọc inventory (version=5, available=1)
        ↓
Request A: UPDATE inventory SET reserved=1 WHERE version=5  →  SUCCESS, version=6
Request B: UPDATE inventory SET reserved=1 WHERE version=5  →  FAIL (version đã là 6)
        ↓
Request B: OptimisticLockingFailureException
        ↓
OrderService.createOrder() bắt exception → retry 1 lần
        ↓
Retry: đọc lại inventory (version=6, available=0)
        ↓
available=0 < requested=1  →  throw "Not enough stock"  →  400 Bad Request
```

### Tại sao không dùng Pessimistic Locking?

| | Optimistic | Pessimistic |
|---|---|---|
| Cơ chế | Version check khi update | Lock row khi đọc (SELECT FOR UPDATE) |
| Performance | Tốt hơn khi ít conflict | Chậm hơn vì lock |
| Deadlock | Không có | Có thể xảy ra |
| Phù hợp | E-commerce thông thường | Khi conflict rất cao |

Optimistic Locking phù hợp hơn cho e-commerce vì phần lớn đặt hàng không conflict nhau.

## Kết quả test

```
Total requests : 50
Success        : <= 10  ✅
Failed         : >= 40  ✅
```

## Chạy test

```bash
# Đảm bảo app đang chạy ở localhost:8081
chmod +x scripts/test_concurrency.sh
./scripts/test_concurrency.sh
```
