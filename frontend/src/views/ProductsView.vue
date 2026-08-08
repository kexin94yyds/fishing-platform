<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search, ShoppingCart } from '@element-plus/icons-vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import ResourceState from '@/components/ResourceState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { memberApi, productApi, saleOrderApi } from '@/api'
import { ApiError, errorMessage } from '@/api/http'
import { formatCurrency, formatDateTime } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import type { Id, Member, Product, SaleOrder } from '@/types'

const auth = useAuthStore()
const canManageProducts = computed(() => auth.isAdmin)
const deskTab = ref('cart')
const loading = ref(true)
const error = ref('')
const products = ref<Product[]>([])
const orders = ref<SaleOrder[]>([])
const members = ref<Member[]>([])
const keyword = ref('')
const categoryFilter = ref('')
const productDialogOpen = ref(false)
const saving = ref(false)
const productFormRef = ref<FormInstance>()
const checkoutDeskRef = ref<HTMLElement>()

type ProductForm = {
  id?: Id
  sku: string
  name: string
  category: string
  price: number
  stockQuantity: number
  status: string
  version?: number
}

type SaleLine = {
  productId: Id
  quantity: number
}

const productForm = reactive<ProductForm>({
  sku: '',
  name: '',
  category: '',
  price: 0,
  stockQuantity: 0,
  status: 'ACTIVE',
})

const saleForm = reactive<{
  memberId: Id | ''
  paymentMethod: string
  items: SaleLine[]
}>({
  memberId: '',
  paymentMethod: '',
  items: [],
})

const productRules: FormRules = {
  sku: [{ required: true, message: '请输入商品编码', trigger: 'blur' }],
  name: [{ required: true, message: '请输入商品名称', trigger: 'blur' }],
  category: [{ required: true, message: '请输入商品分类', trigger: 'blur' }],
  price: [{ required: true, message: '请输入商品售价', trigger: 'change' }],
  stockQuantity: [{ required: true, message: '请输入库存数量', trigger: 'change' }],
}

const categories = computed(() =>
  [...new Set(products.value.map((product) => product.category).filter(Boolean))].sort(),
)

const visibleProducts = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return products.value.filter((product) => {
    const text = `${product.sku || ''} ${product.name} ${product.category || ''}`.toLowerCase()
    return (
      (!query || text.includes(query)) &&
      (!categoryFilter.value || product.category === categoryFilter.value)
    )
  })
})

const saleTotal = computed(() =>
  saleForm.items.reduce((sum, line) => {
    const product = productById(line.productId)
    return sum + (product?.price ?? 0) * line.quantity
  }, 0),
)

const saleItemCount = computed(() =>
  saleForm.items.reduce((sum, line) => sum + line.quantity, 0),
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [productResult, orderResult, memberResult] = await Promise.all([
      productApi.list(),
      saleOrderApi.list(),
      memberApi.list(),
    ])
    products.value = productResult.records
    orders.value = orderResult.records
    members.value = memberResult.records
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function productById(productId: Id) {
  return products.value.find((product) => String(product.id) === String(productId))
}

function selectedStock(productId: Id): number | undefined {
  return productById(productId)?.stockQuantity
}

function selectedQuantity(productId: Id) {
  return saleForm.items.find((line) => String(line.productId) === String(productId))?.quantity ?? 0
}

function isLowStock(product: Product) {
  return product.status === 'ACTIVE' && product.stockQuantity > 0 && product.stockQuantity <= 10
}

function stockLabel(product: Product) {
  if (product.status !== 'ACTIVE') return '已停用'
  if (product.stockQuantity <= 0) return '已售罄'
  if (isLowStock(product)) return `低库存 ${product.stockQuantity}`
  return `库存 ${product.stockQuantity}`
}

function openProduct(product?: Product) {
  Object.assign(productForm, {
    id: product?.id,
    sku: product?.sku ?? '',
    name: product?.name ?? '',
    category: product?.category ?? '',
    price: product?.price ?? 0,
    stockQuantity: product?.stockQuantity ?? 0,
    status: product?.status ?? 'ACTIVE',
    version: product?.version,
  })
  productFormRef.value?.clearValidate()
  productDialogOpen.value = true
}

function addToCart(product: Product) {
  if (product.status !== 'ACTIVE' || product.stockQuantity <= 0) return
  const existing = saleForm.items.find((line) => String(line.productId) === String(product.id))
  if (existing) {
    if (existing.quantity >= product.stockQuantity) {
      ElMessage.warning(`${product.name}已达到可售库存`)
      return
    }
    existing.quantity += 1
  } else {
    saleForm.items.push({ productId: product.id, quantity: 1 })
  }
  deskTab.value = 'cart'
}

function removeSaleLine(index: number) {
  saleForm.items.splice(index, 1)
}

async function focusFirstInvalidProductField() {
  await nextTick()
  const formElement = productFormRef.value?.$el as HTMLElement | undefined
  const firstInvalidField = formElement?.querySelector<HTMLElement>(
    '.el-form-item.is-error input:not([disabled]), .el-form-item.is-error [role="combobox"]:not([aria-disabled="true"])',
  )
  firstInvalidField?.focus()
}

async function saveProduct() {
  if (saving.value) return
  const valid = await productFormRef.value?.validate().catch(() => false)
  if (!valid) {
    await focusFirstInvalidProductField()
    return
  }
  saving.value = true
  try {
    const payload = {
      sku: productForm.sku,
      name: productForm.name,
      category: productForm.category,
      price: productForm.price,
      stockQuantity: productForm.stockQuantity,
      status: productForm.status,
    }
    if (productForm.id !== undefined) {
      await productApi.update(productForm.id, {
        ...payload,
        version: productForm.version ?? 0,
      })
    }
    else await productApi.create(payload)
    ElMessage.success(productForm.id !== undefined ? '商品已更新' : '商品已创建')
    productDialogOpen.value = false
    await load()
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
    if (
      productForm.id !== undefined &&
      reason instanceof ApiError &&
      reason.status === 409 &&
      /刷新|发生变化/.test(reason.message)
    ) {
      productDialogOpen.value = false
      await load()
    }
  } finally {
    saving.value = false
  }
}

async function showCheckout() {
  deskTab.value = 'cart'
  await nextTick()
  const checkoutDesk = checkoutDeskRef.value
  if (!checkoutDesk) return
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  checkoutDesk.scrollIntoView({
    behavior: reduceMotion ? 'auto' : 'smooth',
    block: 'start',
  })
  checkoutDesk.focus({ preventScroll: true })
}

async function createSale() {
  if (!saleForm.items.length) {
    ElMessage.warning('请先从货架选择商品')
    return
  }
  const overstockLine = saleForm.items.find((item) => {
    const stock = selectedStock(item.productId)
    return stock !== undefined && item.quantity > stock
  })
  if (overstockLine) {
    ElMessage.warning(`${productById(overstockLine.productId)?.name || '所选商品'}的数量超过库存`)
    return
  }
  saving.value = true
  try {
    await saleOrderApi.create({
      memberId: saleForm.memberId || undefined,
      paymentMethod: saleForm.paymentMethod || undefined,
      items: saleForm.items.map((item) => ({
        productId: item.productId,
        quantity: item.quantity,
      })),
    })
    ElMessage.success('销售订单已创建')
    saleForm.memberId = ''
    saleForm.paymentMethod = ''
    saleForm.items = []
    deskTab.value = 'history'
    await load()
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="sales-counter">
    <header class="counter-head">
      <div>
        <span class="station-kicker">渔具收银台</span>
        <h1>左边选货，右边结算</h1>
        <p>商品价格和库存来自商品接口，订单金额最终以后端计算结果为准。</p>
      </div>
      <div class="counter-head__actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新货架</el-button>
        <el-button v-if="canManageProducts" type="primary" :icon="Plus" @click="openProduct()">
          新增商品
        </el-button>
      </div>
    </header>

    <ResourceState :loading="loading" :error="error" @retry="load">
      <section class="counter-layout">
        <div class="product-shelf">
          <header class="shelf-tools">
            <el-input
              v-model="keyword"
              :prefix-icon="Search"
              clearable
              name="productSearch"
              aria-label="搜索商品"
              autocomplete="off"
              :spellcheck="false"
              placeholder="搜索编码、名称或分类…"
              style="width: 240px"
            />
            <el-select
              v-model="categoryFilter"
              clearable
              name="categoryFilter"
              aria-label="筛选商品分类"
              placeholder="全部分类"
              style="width: 130px"
            >
              <el-option
                v-for="category in categories"
                :key="category"
                :label="category"
                :value="category"
              />
            </el-select>
            <span>{{ visibleProducts.length }} 件商品</span>
          </header>

          <LakeEmptyState
            v-if="!visibleProducts.length"
            title="货架上没有匹配商品"
            :description="canManageProducts ? '调整搜索词或商品分类，也可以直接新增商品。' : '调整搜索词或商品分类后重试。'"
          >
            <el-button v-if="canManageProducts" type="primary" @click="openProduct()">新增商品</el-button>
          </LakeEmptyState>
          <div v-else class="shelf-grid">
            <article
              v-for="product in visibleProducts"
              :key="product.id"
              class="shelf-item"
              :class="{
                'is-selected': selectedQuantity(product.id) > 0,
                'is-low-stock': isLowStock(product),
              }"
            >
              <header>
                <span>{{ product.category }}</span>
                <div class="shelf-item__tools">
                  <span v-if="selectedQuantity(product.id)" class="selected-count">
                    已选 {{ selectedQuantity(product.id) }}
                  </span>
                  <el-button
                    v-if="canManageProducts"
                    :icon="Edit"
                    text
                    :aria-label="`编辑商品 ${product.name}`"
                    @click.stop="openProduct(product)"
                  />
                </div>
              </header>
              <div>
                <small>{{ product.sku }}</small>
                <h2>{{ product.name }}</h2>
              </div>
              <footer>
                <div>
                  <strong class="metric-value">{{ formatCurrency(product.price) }}</strong>
                  <span
                    class="stock-label"
                    :class="{ 'is-low': isLowStock(product) }"
                  >
                    {{ stockLabel(product) }}
                  </span>
                </div>
                <el-button
                  type="primary"
                  :icon="ShoppingCart"
                  :disabled="product.status !== 'ACTIVE' || product.stockQuantity <= 0"
                  :aria-label="`选入商品 ${product.name}`"
                  @click="addToCart(product)"
                >
                  选入
                </el-button>
              </footer>
            </article>
          </div>
        </div>

        <aside
          ref="checkoutDeskRef"
          class="checkout-desk"
          aria-label="订单结算区"
          tabindex="-1"
        >
          <el-tabs v-model="deskTab">
            <el-tab-pane :label="`当前订单 ${saleItemCount}`" name="cart">
              <el-form class="checkout-form" label-position="top">
                <el-form-item label="关联会员">
                  <el-select
                    v-model="saleForm.memberId"
                    clearable
                    filterable
                    name="memberId"
                    aria-label="关联会员"
                    placeholder="可不选择，按散客结算…"
                    style="width: 100%"
                  >
                    <el-option
                      v-for="member in members.filter((item) => item.status === 'ACTIVE')"
                      :key="member.id"
                      :label="`${member.name} ${member.phone}`"
                      :value="member.id"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="支付方式">
                  <el-select
                    v-model="saleForm.paymentMethod"
                    clearable
                    name="paymentMethod"
                    aria-label="支付方式"
                    placeholder="可稍后到收费账房确认…"
                    style="width: 100%"
                  >
                    <el-option label="现金" value="CASH" />
                    <el-option label="微信" value="WECHAT" />
                    <el-option label="支付宝" value="ALIPAY" />
                    <el-option label="银行卡" value="CARD" />
                  </el-select>
                </el-form-item>
              </el-form>

              <LakeEmptyState
                v-if="!saleForm.items.length"
                title="订单还没有商品"
                description="从货架选入商品后，可在这里调整数量并结算。"
                compact
              />
              <div v-else class="cart-lines">
                <article v-for="(line, index) in saleForm.items" :key="line.productId">
                  <div>
                    <strong>{{ productById(line.productId)?.name || '商品已变更' }}</strong>
                    <span>{{ formatCurrency(productById(line.productId)?.price) }}</span>
                  </div>
                  <el-input-number
                    v-model="line.quantity"
                    :min="1"
                    :max="selectedStock(line.productId)"
                    controls-position="right"
                    size="small"
                    :name="`quantity-${line.productId}`"
                    :aria-label="`${productById(line.productId)?.name || '商品'}的购买数量`"
                  />
                  <el-button
                    :icon="Delete"
                    circle
                    text
                    :aria-label="`移除 ${productById(line.productId)?.name || '商品'}`"
                    @click="removeSaleLine(index)"
                  />
                </article>
              </div>

              <div class="checkout-total">
                <span>预计合计</span>
                <strong class="metric-value">{{ formatCurrency(saleTotal) }}</strong>
                <small>最终金额以后端订单为准</small>
              </div>
              <el-button
                type="primary"
                class="checkout-submit"
                :loading="saving"
                :disabled="!saleForm.items.length"
                @click="createSale"
              >
                创建销售订单
              </el-button>
            </el-tab-pane>

            <el-tab-pane label="订单历史" name="history">
              <LakeEmptyState
                v-if="!orders.length"
                title="暂无销售订单"
                description="完成首笔销售后，订单记录会显示在这里。"
              />
              <div v-else class="order-history">
                <article v-for="order in orders" :key="order.id">
                  <header>
                    <strong>{{ order.orderNo || order.id }}</strong>
                    <StatusTag :status="order.paymentStatus || order.status" />
                  </header>
                  <div>
                    <span>{{ order.memberName || '散客' }}</span>
                    <strong class="metric-value">{{ formatCurrency(order.totalAmount) }}</strong>
                  </div>
                  <small>{{ formatDateTime(order.createdAt) }}</small>
                </article>
              </div>
            </el-tab-pane>
          </el-tabs>
        </aside>
      </section>
    </ResourceState>

    <aside
      v-if="!productDialogOpen"
      class="mobile-order-bar"
      aria-label="当前订单摘要"
    >
      <div class="mobile-order-bar__summary" aria-live="polite" aria-atomic="true">
        <span>已选 {{ saleItemCount }} 件</span>
        <strong>预计合计 {{ formatCurrency(saleTotal) }}</strong>
      </div>
      <el-button type="primary" class="mobile-order-bar__action" @click="showCheckout">
        查看 / 结算
      </el-button>
    </aside>

    <el-dialog
      v-if="canManageProducts"
      v-model="productDialogOpen"
      :title="productForm.id !== undefined ? '编辑商品' : '新增商品'"
      width="620px"
      destroy-on-close
    >
      <el-form
        id="product-form"
        ref="productFormRef"
        :model="productForm"
        :rules="productRules"
        label-position="top"
        aria-live="polite"
        @submit.prevent="saveProduct"
      >
        <div class="form-grid">
          <el-form-item label="商品编码" prop="sku">
            <el-input
              v-model.trim="productForm.sku"
              name="sku"
              autocomplete="off"
              :spellcheck="false"
              placeholder="例如 BAIT-001…"
            />
          </el-form-item>
          <el-form-item label="商品名称" prop="name">
            <el-input
              v-model.trim="productForm.name"
              name="name"
              autocomplete="off"
              placeholder="例如 湖畔玉米饵…"
            />
          </el-form-item>
          <el-form-item label="商品分类" prop="category">
            <el-input
              v-model.trim="productForm.category"
              name="category"
              autocomplete="off"
              placeholder="例如 鱼饵…"
            />
          </el-form-item>
          <el-form-item label="商品状态">
            <el-select
              v-model="productForm.status"
              name="status"
              aria-label="商品状态"
              style="width: 100%"
            >
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="销售价格" prop="price">
            <el-input-number
              v-model="productForm.price"
              :min="0"
              :precision="2"
              controls-position="right"
              name="price"
              aria-label="销售价格"
            />
          </el-form-item>
          <el-form-item label="库存数量" prop="stockQuantity">
            <el-input-number
              v-model="productForm.stockQuantity"
              :min="0"
              controls-position="right"
              name="stockQuantity"
              aria-label="库存数量"
            />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="productDialogOpen = false">取消</el-button>
        <el-button
          type="primary"
          native-type="submit"
          form="product-form"
          :loading="saving"
        >
          保存商品
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sales-counter :deep(.el-button),
.sales-counter :deep(.el-tabs__item),
.sales-counter :deep(.el-select__wrapper),
.sales-counter :deep(.el-input__wrapper),
.sales-counter :deep(.el-input-number) {
  touch-action: manipulation;
}

.sales-counter :deep(.el-button:focus-visible),
.sales-counter :deep(.el-tabs__item:focus-visible),
.checkout-desk:focus-visible {
  outline: 3px solid rgb(158 79 55 / 45%);
  outline-offset: 2px;
}

.sales-counter :deep(.el-input__wrapper:focus-within),
.sales-counter :deep(.el-select__wrapper:focus-within),
.sales-counter :deep(.el-input-number:focus-within) {
  box-shadow: 0 0 0 2px var(--paper-50), 0 0 0 4px rgb(158 79 55 / 42%);
}

.counter-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 15px;
}

.counter-head h1 {
  margin: 0;
  color: var(--lake-900);
  font-size: clamp(25px, 2.3vw, 34px);
  letter-spacing: -0.04em;
}

.counter-head p {
  margin: 6px 0 0;
  color: var(--ink-500);
  font-size: 11px;
}

.counter-head__actions,
.shelf-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.counter-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 370px;
  gap: 14px;
}

.product-shelf {
  min-width: 0;
}

.shelf-tools {
  margin-bottom: 11px;
  padding: 10px;
  border: 1px solid #e2dacd;
  border-radius: 11px;
  background: var(--paper-100);
}

.shelf-tools > span {
  margin-left: auto;
  color: var(--ink-500);
  font-size: 10px;
}

.shelf-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 10px;
}

.shelf-item {
  display: flex;
  min-height: 190px;
  flex-direction: column;
  padding: 14px;
  border: 1px solid #ded5c8;
  border-radius: 12px;
  background: var(--paper-50);
  transition:
    border-color 140ms ease,
    box-shadow 140ms ease;
}

.shelf-item.is-selected {
  border-color: var(--lake-700);
  box-shadow: 0 0 0 1px var(--lake-700) inset;
}

.shelf-item.is-low-stock:not(.is-selected) {
  border-color: var(--status-attention-border);
}

.shelf-item > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.shelf-item > header span {
  color: var(--clay-700);
  font-size: 11px;
  font-weight: 700;
}

.shelf-item__tools {
  display: flex;
  align-items: center;
  gap: 5px;
}

.shelf-item__tools .selected-count {
  padding: 3px 6px;
  border-radius: 999px;
  color: var(--status-positive-text);
  background: var(--status-positive-bg);
  font-size: 10px;
  font-weight: 750;
}

.shelf-item > div {
  margin: 14px 0 20px;
}

.shelf-item small {
  color: #9a8d7d;
  font-size: 11px;
}

.shelf-item h2 {
  margin: 5px 0 0;
  color: var(--ink-900);
  font-size: 16px;
}

.shelf-item footer {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 10px;
  margin-top: auto;
}

.shelf-item footer strong,
.shelf-item footer span {
  display: block;
}

.shelf-item footer strong {
  color: var(--lake-900);
  font-size: 17px;
}

.shelf-item footer span {
  margin-top: 4px;
  color: var(--ink-500);
  font-size: 11px;
}

.shelf-item footer .stock-label.is-low {
  color: var(--status-attention-text);
  font-weight: 750;
}

.checkout-desk {
  position: sticky;
  top: 76px;
  align-self: start;
  max-height: calc(100dvh - 96px);
  overflow-y: auto;
  padding: 15px;
  border: 1px solid #d7c9b9;
  border-radius: 14px;
  background: var(--paper-100);
  scroll-margin-top: 88px;
}

.checkout-form {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
}

.checkout-form :deep(.el-form-item) {
  margin-bottom: 0;
}

.checkout-form :deep(.el-form-item__label) {
  padding-bottom: 4px;
  color: #63716a;
  font-size: 10px;
}

.cart-lines {
  display: grid;
  gap: 6px;
}

.cart-lines article {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 112px 32px;
  align-items: center;
  gap: 7px;
  padding: 9px;
  border-radius: 9px;
  background: var(--paper-50);
}

.cart-lines :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.cart-lines strong,
.cart-lines span {
  display: block;
}

.cart-lines strong {
  overflow: hidden;
  color: #34443d;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cart-lines span {
  margin-top: 3px;
  color: var(--ink-500);
  font-size: 11px;
}

.checkout-total {
  margin-top: 13px;
  padding: 14px;
  border-top: 1px solid var(--paper-400);
  border-bottom: 1px solid var(--paper-400);
}

.checkout-total span,
.checkout-total strong,
.checkout-total small {
  display: block;
}

.checkout-total span {
  color: var(--ink-500);
  font-size: 10px;
}

.checkout-total strong {
  margin-top: 5px;
  color: var(--lake-900);
  font-size: 26px;
}

.checkout-total small {
  margin-top: 4px;
  color: #9a8d7d;
  font-size: 11px;
}

.checkout-submit {
  width: 100%;
  margin-top: 11px;
}

.order-history {
  display: grid;
  gap: 7px;
}

.order-history article {
  padding: 11px;
  border: 1px solid #e2dacd;
  border-radius: 9px;
  background: var(--paper-50);
}

.order-history header,
.order-history article > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 9px;
}

.order-history header > strong {
  color: var(--lake-900);
  font-size: 10px;
}

.order-history article > div {
  margin-top: 10px;
}

.order-history article > div span {
  color: var(--ink-500);
  font-size: 11px;
}

.order-history article > div strong {
  font-size: 14px;
}

.order-history article > small {
  display: block;
  margin-top: 5px;
  color: #9a8d7d;
  font-size: 11px;
}

.mobile-order-bar {
  display: none;
}

.mobile-order-bar__summary {
  min-width: 0;
}

.mobile-order-bar__summary span,
.mobile-order-bar__summary strong {
  display: block;
}

.mobile-order-bar__summary span {
  color: var(--ink-500);
  font-size: 11px;
}

.mobile-order-bar__summary strong {
  overflow: hidden;
  margin-top: 3px;
  color: var(--lake-900);
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (pointer: coarse) {
  .sales-counter :deep(.el-button),
  .sales-counter :deep(.el-tabs__item),
  .sales-counter :deep(.el-input__wrapper),
  .sales-counter :deep(.el-select__wrapper),
  .sales-counter :deep(.el-input-number) {
    min-height: 44px;
  }

  .sales-counter :deep(.el-button.is-circle) {
    min-width: 44px;
  }

  .sales-counter :deep(.el-input-number.is-controls-right .el-input-number__decrease),
  .sales-counter :deep(.el-input-number.is-controls-right .el-input-number__increase) {
    top: 0;
    bottom: 0;
    width: 44px;
    min-height: 44px;
  }

  .sales-counter :deep(.el-input-number.is-controls-right .el-input-number__decrease) {
    right: auto;
    left: 0;
    border-right: var(--el-border);
    border-left: 0;
    border-radius: var(--el-border-radius-base) 0 0 var(--el-border-radius-base);
  }

  .sales-counter :deep(.el-input-number.is-controls-right .el-input-number__increase) {
    border-bottom: 0;
    border-left: var(--el-border);
    border-radius: 0 var(--el-border-radius-base) var(--el-border-radius-base) 0;
  }

  .sales-counter :deep(.el-input-number.is-controls-right .el-input__wrapper) {
    padding-right: 48px;
    padding-left: 48px;
  }

  .cart-lines article {
    grid-template-columns: minmax(0, 1fr) minmax(132px, 148px) 44px;
  }
}

@media (max-width: 980px) {
  .counter-layout {
    grid-template-columns: 1fr;
  }

  .checkout-desk {
    position: static;
    max-height: none;
  }
}

@media (max-width: 640px) {
  .counter-head {
    display: block;
  }

  .counter-head__actions {
    margin-top: 13px;
  }

  .shelf-tools > * {
    width: 100% !important;
  }

  .shelf-tools > span {
    margin-left: 0;
  }

  .shelf-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 759px) {
  .sales-counter {
    padding-bottom: calc(78px + env(safe-area-inset-bottom));
  }

  .mobile-order-bar {
    position: fixed;
    z-index: 40;
    right: 0;
    bottom: 0;
    left: 0;
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 12px;
    min-height: 68px;
    padding: 10px max(12px, env(safe-area-inset-right))
      calc(10px + env(safe-area-inset-bottom)) max(12px, env(safe-area-inset-left));
    border-top: 1px solid #d7c9b9;
    background: rgb(255 253 248 / 96%);
    box-shadow: 0 -10px 28px rgb(23 70 58 / 12%);
    backdrop-filter: blur(12px);
  }

  .mobile-order-bar__action {
    min-width: 116px;
    min-height: 44px;
  }
}

@media (max-width: 430px) {
  .shelf-grid {
    grid-template-columns: 1fr;
  }

  .cart-lines article {
    grid-template-columns: minmax(0, 1fr) 44px;
  }

  .cart-lines article > div {
    grid-column: 1 / -1;
  }
}
</style>
