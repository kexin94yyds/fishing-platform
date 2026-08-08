<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Close, Refresh, Search } from '@element-plus/icons-vue'
import ResourceState from '@/components/ResourceState.vue'
import LakeEmptyState from '@/components/LakeEmptyState.vue'
import StatusTag from '@/components/StatusTag.vue'
import { paymentApi, saleOrderApi } from '@/api'
import { errorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import {
  businessTypeLabel,
  formatCurrency,
  formatDateTime,
  paymentMethodLabel,
} from '@/utils/format'
import type { Payment, SaleOrder } from '@/types'

const auth = useAuthStore()
const canConfirmPayments = computed(() => auth.isAdmin)
const loading = ref(true)
const error = ref('')
const payments = ref<Payment[]>([])
const orders = ref<SaleOrder[]>([])
const keyword = ref('')
const statusFilter = ref('')
const confirmingId = ref<Payment['id'] | null>(null)
const cancellingId = ref<Payment['id'] | null>(null)
const confirmDialogOpen = ref(false)
const selectedPayment = ref<Payment | null>(null)
const paymentMethod = ref('CASH')

const visiblePayments = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return payments.value.filter((payment) => {
    const text =
      `${payment.paymentNo || ''} ${payment.businessNo || ''} ${payment.businessType || ''}`.toLowerCase()
    return (!query || text.includes(query)) && (!statusFilter.value || payment.status === statusFilter.value)
  })
})

const pendingPayments = computed(() =>
  payments.value.filter((payment) => payment.status === 'PENDING'),
)

const pendingAmount = computed(() =>
  pendingPayments.value.reduce((sum, payment) => sum + Number(payment.amount || 0), 0),
)

const confirmedAmount = computed(() =>
  payments.value
    .filter((payment) => payment.status === 'PAID')
    .reduce((sum, payment) => sum + Number(payment.amount || 0), 0),
)

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [paymentResult, orderResult] = await Promise.all([
      paymentApi.list(),
      saleOrderApi.list(),
    ])
    payments.value = paymentResult.records
    orders.value = orderResult.records
  } catch (reason) {
    error.value = errorMessage(reason)
  } finally {
    loading.value = false
  }
}

function openConfirm(payment: Payment) {
  if (!canConfirmPayments.value) return
  selectedPayment.value = payment
  paymentMethod.value = payment.method || 'CASH'
  confirmDialogOpen.value = true
}

async function confirmPayment() {
  if (!canConfirmPayments.value || !selectedPayment.value) return
  const payment = selectedPayment.value
  confirmingId.value = payment.id
  try {
    await paymentApi.confirm(payment.id, paymentMethod.value)
    ElMessage.success('到账已核实')
    confirmDialogOpen.value = false
    selectedPayment.value = null
    await load()
  } catch (reason) {
    ElMessage.error(errorMessage(reason))
  } finally {
    confirmingId.value = null
  }
}

async function cancelSale(payment: Payment) {
  if (
    !canConfirmPayments.value ||
    payment.businessType !== 'SALES_ORDER' ||
    payment.businessId === undefined
  ) return
  try {
    await ElMessageBox.confirm(
      `确认取消销售单 ${payment.businessNo || payment.businessId} 吗？已扣商品将自动回补库存。`,
      '取消待收款销售单',
      {
        confirmButtonText: '取消并回补',
        cancelButtonText: '返回',
        type: 'warning',
      },
    )
    cancellingId.value = payment.id
    await saleOrderApi.cancel(payment.businessId)
    ElMessage.success('销售单已取消，商品库存已回补')
    await load()
  } catch (reason) {
    if (reason !== 'cancel' && reason !== 'close') ElMessage.error(errorMessage(reason))
  } finally {
    cancellingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="payment-office">
    <header class="payment-head">
      <div>
        <span class="station-kicker">收费账房</span>
        <h1>先清待办，再对账目</h1>
        <p>{{ canConfirmPayments ? '现场到账确认会同步更新关联销售订单。' : '可查看收费记录，到账由管理员核实。' }}</p>
      </div>
      <el-button :icon="Refresh" :loading="loading" @click="load">刷新账目</el-button>
    </header>

    <ResourceState :loading="loading" :error="error" @retry="load">
      <section class="payment-workbench">
        <aside class="pending-counter">
          <header>
            <div>
              <span>待处理队列</span>
              <strong>{{ pendingPayments.length }} 笔</strong>
            </div>
            <p>
              待收合计
              <b class="metric-value">{{ formatCurrency(pendingAmount) }}</b>
            </p>
          </header>

          <LakeEmptyState
            v-if="!pendingPayments.length"
            title="待办已经清空"
            description="当前没有需要确认的现场收款"
            compact
            inverted
          />
          <div v-else class="pending-list">
            <article
              v-for="(payment, index) in pendingPayments"
              :key="payment.id"
              class="payment-ticket"
            >
              <div class="payment-ticket__top">
                <span>队列 {{ String(index + 1).padStart(2, '0') }}</span>
                <StatusTag :status="payment.status" />
              </div>
              <strong class="payment-ticket__amount metric-value">
                {{ formatCurrency(payment.amount) }}
              </strong>
              <div class="payment-ticket__reference">
                <b>{{ payment.businessNo || '暂无业务单号' }}</b>
                <small>{{ businessTypeLabel(payment.businessType) }}</small>
              </div>
              <div class="payment-ticket__foot">
                <span>{{ payment.paymentNo || payment.id }}</span>
                <div class="payment-ticket__actions">
                  <el-button
                    v-if="canConfirmPayments"
                    type="primary"
                    :icon="Check"
                    :loading="confirmingId === payment.id"
                    @click="openConfirm(payment)"
                  >
                    核实到账
                  </el-button>
                  <el-button
                    v-if="canConfirmPayments && payment.businessType === 'SALES_ORDER'"
                    type="danger"
                    plain
                    :icon="Close"
                    :loading="cancellingId === payment.id"
                    @click="cancelSale(payment)"
                  >
                    取消订单
                  </el-button>
                </div>
              </div>
            </article>
          </div>
        </aside>

        <section class="ledger-book">
          <header class="ledger-head">
            <div>
              <span class="station-kicker">收支账目</span>
              <h2>支付流水</h2>
            </div>
            <div class="ledger-tools">
              <el-input
                v-model="keyword"
                :prefix-icon="Search"
                clearable
                placeholder="支付单号或业务单号"
                aria-label="搜索支付流水"
              />
              <el-select
                v-model="statusFilter"
                clearable
                placeholder="全部状态"
                aria-label="按支付状态筛选"
              >
                <el-option label="待确认" value="PENDING" />
                <el-option label="已确认" value="PAID" />
                <el-option label="已取消" value="CANCELLED" />
                <el-option label="失败" value="FAILED" />
              </el-select>
            </div>
          </header>

          <div class="ledger-totals" aria-label="账目汇总">
            <div>
              <span>已确认金额</span>
              <strong class="metric-value">{{ formatCurrency(confirmedAmount) }}</strong>
            </div>
            <div>
              <span>待确认金额</span>
              <strong class="metric-value">{{ formatCurrency(pendingAmount) }}</strong>
            </div>
            <div>
              <span>关联销售订单</span>
              <strong class="metric-value">{{ orders.length }} 单</strong>
            </div>
          </div>

          <ResourceState
            :empty="!visiblePayments.length"
            empty-title="没有符合条件的支付流水"
            empty-description="清除筛选条件后可查看全部记录"
          >
            <div class="ledger-list">
              <div class="ledger-row ledger-row--labels" aria-hidden="true">
                <span>支付单</span>
                <span>关联业务</span>
                <span>金额</span>
                <span>方式</span>
                <span>状态</span>
                <span>确认时间</span>
              </div>
              <article
                v-for="payment in visiblePayments"
                :key="payment.id"
                class="ledger-row"
              >
                <div data-label="支付单">
                  <strong>{{ payment.paymentNo || payment.id }}</strong>
                  <small>{{ formatDateTime(payment.createdAt) }}</small>
                </div>
                <div data-label="关联业务">
                  <strong>{{ payment.businessNo || '暂无单号' }}</strong>
                  <small>{{ businessTypeLabel(payment.businessType) }}</small>
                </div>
                <strong class="ledger-row__amount metric-value" data-label="金额">
                  {{ formatCurrency(payment.amount) }}
                </strong>
                <span data-label="方式">{{ paymentMethodLabel(payment.method) }}</span>
                <div data-label="状态"><StatusTag :status="payment.status" /></div>
                <div class="ledger-row__time" data-label="确认时间">
                  <span>{{ formatDateTime(payment.confirmedAt) }}</span>
                  <el-button
                    v-if="canConfirmPayments && payment.status === 'PENDING'"
                    link
                    type="primary"
                    :aria-label="`核实到账 ${payment.paymentNo || payment.id}`"
                    @click="openConfirm(payment)"
                  >
                    核实到账
                  </el-button>
                  <el-button
                    v-if="canConfirmPayments && payment.status === 'PENDING' && payment.businessType === 'SALES_ORDER'"
                    link
                    type="danger"
                    :loading="cancellingId === payment.id"
                    :aria-label="`取消销售单 ${payment.businessNo || payment.businessId}`"
                    @click="cancelSale(payment)"
                  >
                    取消订单
                  </el-button>
                </div>
              </article>
            </div>
          </ResourceState>
        </section>
      </section>

      <section class="order-stubs">
        <header>
          <div>
            <span class="station-kicker">关联存根</span>
            <h2>销售订单回查</h2>
          </div>
          <span>共 {{ orders.length }} 单</span>
        </header>
        <LakeEmptyState
          v-if="!orders.length"
          title="暂无关联销售订单"
          description="现场销售订单生成后，可在这里回查存根"
          compact
        />
        <div v-else class="order-stub-list">
          <article v-for="order in orders" :key="order.id">
            <div>
              <small>销售单</small>
              <strong>{{ order.orderNo || order.id }}</strong>
            </div>
            <div>
              <small>客户</small>
              <span>{{ order.memberName || '散客' }}</span>
            </div>
            <strong class="metric-value">{{ formatCurrency(order.totalAmount) }}</strong>
            <div class="order-stub__status">
              <StatusTag :status="order.paymentStatus" />
              <small>{{ formatDateTime(order.createdAt) }}</small>
            </div>
          </article>
        </div>
      </section>
    </ResourceState>

    <el-dialog
      v-if="canConfirmPayments"
      v-model="confirmDialogOpen"
      title="核实到账"
      width="520px"
      destroy-on-close
    >
      <div v-if="selectedPayment" class="confirm-payment">
        <div class="confirm-payment__summary">
          <span>待确认金额</span>
          <strong class="metric-value">{{ formatCurrency(selectedPayment.amount) }}</strong>
          <small>{{ selectedPayment.paymentNo || selectedPayment.id }}</small>
        </div>
        <el-form label-position="top">
          <el-form-item label="收款方式" required>
            <el-select v-model="paymentMethod" aria-label="收款方式" style="width: 100%">
              <el-option label="现金" value="CASH" />
              <el-option label="微信" value="WECHAT" />
              <el-option label="支付宝" value="ALIPAY" />
              <el-option label="银行卡" value="CARD" />
            </el-select>
          </el-form-item>
        </el-form>
        <el-alert
          title="核实到账后将同步完成关联销售订单，此操作不能重复执行"
          type="info"
          :closable="false"
          show-icon
        />
      </div>
      <template #footer>
        <el-button @click="confirmDialogOpen = false">返回核对</el-button>
        <el-button
          type="primary"
          :loading="selectedPayment ? confirmingId === selectedPayment.id : false"
          @click="confirmPayment"
        >
          核实到账
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.payment-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 22px;
  margin-bottom: 15px;
}

.payment-head h1 {
  margin: 0;
  color: var(--lake-900);
  font-size: clamp(25px, 2.3vw, 34px);
  letter-spacing: -0.04em;
}

.payment-head p {
  margin: 6px 0 0;
  color: var(--ink-500);
  font-size: 11px;
}

.payment-workbench {
  display: grid;
  grid-template-columns: minmax(300px, 0.7fr) minmax(620px, 1.65fr);
  overflow: hidden;
  min-height: 570px;
  border: 1px solid #ded5c8;
  border-radius: 14px;
  background: var(--paper-50);
}

.pending-counter {
  min-width: 0;
  padding: 18px;
  color: #f8f1e7;
  background: var(--lake-900);
}

.pending-counter > header {
  padding: 4px 3px 16px;
  border-bottom: 1px solid rgb(255 255 255 / 15%);
}

.pending-counter > header > div,
.pending-counter > header p {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.pending-counter > header span {
  color: #c5d4cc;
  font-size: 12px;
  letter-spacing: 0.08em;
}

.pending-counter > header strong {
  font-size: 25px;
}

.pending-counter > header p {
  margin: 11px 0 0;
  color: #b8c9c1;
  font-size: 11px;
}

.pending-counter > header b {
  color: #fff8ef;
  font-size: 17px;
}

.pending-list {
  display: grid;
  gap: 10px;
  padding-top: 14px;
}

.payment-ticket {
  padding: 14px;
  border: 1px solid rgb(255 255 255 / 13%);
  border-radius: 10px;
  color: #2f3c37;
  background: #fffaf1;
}

.payment-ticket__top,
.payment-ticket__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.payment-ticket__top > span {
  color: #9b6959;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.payment-ticket__amount {
  display: block;
  margin: 14px 0 12px;
  color: var(--lake-900);
  font-size: 25px;
}

.payment-ticket__reference {
  display: grid;
  gap: 2px;
  padding-bottom: 12px;
  border-bottom: 1px dashed #d8cdbd;
}

.payment-ticket__reference b {
  font-size: 13px;
}

.payment-ticket__reference small,
.payment-ticket__foot > span {
  color: #7a847e;
  font-size: 10px;
}

.payment-ticket__foot {
  margin-top: 11px;
}

.payment-ticket__actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 6px;
}

.payment-ticket__actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.ledger-book {
  min-width: 0;
  padding: 18px 20px 20px;
}

.ledger-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 15px;
  border-bottom: 2px solid var(--lake-900);
}

.ledger-head h2,
.order-stubs h2 {
  margin: 2px 0 0;
  color: #22372f;
  font-size: 20px;
}

.ledger-tools {
  display: grid;
  grid-template-columns: minmax(190px, 1fr) 125px;
  gap: 8px;
  width: min(410px, 58%);
}

.ledger-totals {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-bottom: 1px solid #ded5c8;
}

.ledger-totals > div {
  padding: 14px 10px;
}

.ledger-totals > div + div {
  border-left: 1px solid #e4ddd2;
}

.ledger-totals span,
.ledger-totals strong {
  display: block;
}

.ledger-totals span {
  color: #879089;
  font-size: 10px;
}

.ledger-totals strong {
  margin-top: 5px;
  color: var(--lake-900);
  font-size: 17px;
}

.ledger-list {
  margin-top: 8px;
}

.ledger-row {
  display: grid;
  grid-template-columns: minmax(130px, 1.15fr) minmax(125px, 1fr) minmax(100px, 0.75fr) 70px 76px minmax(112px, 0.85fr);
  align-items: center;
  gap: 10px;
  min-height: 60px;
  padding: 8px 6px;
  border-bottom: 1px solid #ebe4d9;
  color: #55645e;
  font-size: 11px;
}

.ledger-row--labels {
  min-height: 35px;
  color: #949c96;
  font-size: 11px;
  letter-spacing: 0.08em;
}

.ledger-row > div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.ledger-row strong {
  overflow: hidden;
  color: #243b32;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ledger-row small {
  color: #929991;
  font-size: 11px;
}

.ledger-row__amount {
  color: var(--lake-900) !important;
  font-size: 13px !important;
}

.ledger-row__time {
  grid-template-columns: minmax(0, 1fr) auto !important;
  align-items: center;
}

.order-stubs {
  margin-top: 14px;
  padding: 17px 19px 19px;
  border: 1px solid #ded5c8;
  border-radius: 14px;
  background: #f8f2e7;
}

.order-stubs > header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 13px;
}

.order-stubs > header > span {
  color: #878d88;
  font-size: 10px;
}

.order-stub-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(220px, 1fr));
  gap: 8px;
}

.order-stub-list article {
  display: grid;
  grid-template-columns: 1.25fr 0.85fr auto;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid #ded5c8;
  border-radius: 9px;
  background: var(--paper-50);
}

.order-stub-list article > div {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.order-stub-list small {
  color: #91958f;
  font-size: 11px;
}

.order-stub-list strong,
.order-stub-list span {
  overflow: hidden;
  color: #34483f;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.order-stub-list > article > strong {
  color: var(--lake-900);
  font-size: 13px;
}

.order-stub__status {
  grid-column: 1 / -1;
  grid-template-columns: auto 1fr !important;
  align-items: center;
  padding-top: 8px;
  border-top: 1px dashed #ddd3c4;
}

.order-stub__status small {
  text-align: right;
}

.confirm-payment {
  display: grid;
  gap: 18px;
}

.confirm-payment__summary {
  display: grid;
  gap: 4px;
  padding: 18px;
  border-left: 4px solid var(--clay-600);
  background: #f5eee3;
}

.confirm-payment__summary span,
.confirm-payment__summary small {
  color: #7d8881;
  font-size: 11px;
}

.confirm-payment__summary strong {
  color: var(--lake-900);
  font-size: 26px;
}

@media (max-width: 1140px) {
  .payment-workbench {
    grid-template-columns: 300px minmax(0, 1fr);
  }

  .ledger-row {
    grid-template-columns: minmax(120px, 1.15fr) minmax(110px, 1fr) minmax(90px, 0.8fr) 68px 74px;
  }

  .ledger-row--labels span:last-child,
  .ledger-row__time {
    display: none;
  }

  .order-stub-list {
    grid-template-columns: repeat(2, minmax(220px, 1fr));
  }
}

@media (max-width: 860px) {
  .payment-workbench {
    grid-template-columns: 1fr;
  }

  .pending-counter {
    max-height: none;
  }

  .pending-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .payment-head,
  .ledger-head {
    align-items: stretch;
    flex-direction: column;
  }

  .payment-head > .el-button {
    width: 100%;
  }

  .pending-list {
    grid-template-columns: 1fr;
  }

  .ledger-book {
    padding: 15px;
  }

  .ledger-tools {
    grid-template-columns: 1fr;
    width: 100%;
  }

  .ledger-totals {
    grid-template-columns: 1fr;
  }

  .ledger-totals > div + div {
    border-top: 1px solid #e4ddd2;
    border-left: 0;
  }

  .ledger-row--labels {
    display: none;
  }

  .ledger-row {
    grid-template-columns: 1fr 1fr;
    gap: 13px;
    padding: 14px 2px;
  }

  .ledger-row > *::before {
    display: block;
    margin-bottom: 3px;
    color: #999f99;
    font-size: 11px;
    letter-spacing: 0.08em;
    content: attr(data-label);
  }

  .ledger-row__time {
    display: grid;
  }

  .order-stub-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 390px) {
  .payment-head h1 {
    font-size: 25px;
  }

  .pending-counter,
  .ledger-book,
  .order-stubs {
    padding-right: 12px;
    padding-left: 12px;
  }
}
</style>
