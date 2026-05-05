import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { financialAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '../components/ui/dialog';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '../components/ui/select';
import {
  Table, TableBody, TableCell, TableHead, TableHeader, TableRow,
} from '../components/ui/table';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import { Plus, DollarSign, CheckCircle, CreditCard, Trash2, CalendarClock, Play } from 'lucide-react';

const STATUS_VARIANTS = {
  PENDING: 'secondary',
  APPROVED: 'outline',
  PAID: 'default',
  CANCELLED: 'destructive',
};

export default function Payroll() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [employees, setEmployees] = useState([]);
  const [payrolls, setPayrolls] = useState([]);
  const [salaryConfigs, setSalaryConfigs] = useState([]);
  const [createOpen, setCreateOpen] = useState(false);
  const [salaryOpen, setSalaryOpen] = useState(false);
  const [payOpen, setPayOpen] = useState(false);
  const [selectedPayroll, setSelectedPayroll] = useState(null);
  const [form, setForm] = useState({
    employeeId: '', payrollType: 'HOURLY', payPeriodStart: '', payPeriodEnd: '',
    hoursWorked: '', hourlyRate: '', baseSalary: '', overtimePay: '',
    bonus: '', tips: '', commission: '', taxDeduction: '', otherDeductions: '', notes: '',
  });
  const [salaryForm, setSalaryForm] = useState({
    employeeId: '', monthlySalary: '', payDay: '1', paymentMethod: 'CASH', autoApprove: true, notes: '',
  });
  const [payForm, setPayForm] = useState({ paymentDate: new Date().toISOString().split('T')[0], paymentMethod: 'CASH', transactionRef: '' });

  useEffect(() => {
    restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
      const list = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
      if (list.length > 0) setSelectedRestaurant(list[0].id.toString());
    }).catch(console.error);
    financialAPI.getPayrollEmployees().then(res => {
      setEmployees(res.data.data || []);
    }).catch(console.error);
  }, []);

  useEffect(() => {
    if (selectedRestaurant) {
      loadPayrolls();
      loadSalaryConfigs();
    }
  }, [selectedRestaurant]);

  const loadPayrolls = async () => {
    try {
      const res = await financialAPI.getPayroll(selectedRestaurant);
      setPayrolls(res.data.data || []);
    } catch (e) { console.error('Failed to load payroll:', e); }
  };

  const loadSalaryConfigs = async () => {
    try {
      const res = await financialAPI.getSalaryConfigs(selectedRestaurant);
      setSalaryConfigs(res.data.data || []);
    } catch (e) { console.error('Failed to load salary configs:', e); }
  };

  const handleCreateSalary = async () => {
    try {
      await financialAPI.createSalaryConfig({
        restaurantId: parseInt(selectedRestaurant),
        employeeId: parseInt(salaryForm.employeeId),
        monthlySalary: parseFloat(salaryForm.monthlySalary),
        payDay: parseInt(salaryForm.payDay),
        paymentMethod: salaryForm.paymentMethod,
        autoApprove: salaryForm.autoApprove,
        notes: salaryForm.notes || null,
      });
      setSalaryOpen(false);
      setSalaryForm({ employeeId: '', monthlySalary: '', payDay: '1', paymentMethod: 'CASH', autoApprove: true, notes: '' });
      loadSalaryConfigs();
    } catch (e) { console.error('Failed:', e); alert(e.response?.data?.message || 'Failed to create salary config'); }
  };

  const handleDeleteSalary = async (id) => {
    if (!window.confirm('Deactivate this salary config?')) return;
    try {
      await financialAPI.deleteSalaryConfig(id);
      loadSalaryConfigs();
    } catch (e) { console.error('Failed:', e); }
  };

  const handlePayNow = async (id) => {
    if (!window.confirm('Process salary payment now?')) return;
    try {
      await financialAPI.paySalaryNow(id);
      loadSalaryConfigs();
      loadPayrolls();
    } catch (e) { console.error('Failed:', e); alert(e.response?.data?.message || 'Failed'); }
  };

  const handleCreate = async () => {
    try {
      await financialAPI.createPayroll({
        restaurantId: parseInt(selectedRestaurant),
        employeeId: parseInt(form.employeeId),
        payrollType: form.payrollType,
        payPeriodStart: form.payPeriodStart,
        payPeriodEnd: form.payPeriodEnd,
        hoursWorked: form.hoursWorked ? parseFloat(form.hoursWorked) : null,
        hourlyRate: form.hourlyRate ? parseFloat(form.hourlyRate) : null,
        baseSalary: form.baseSalary ? parseFloat(form.baseSalary) : null,
        overtimePay: form.overtimePay ? parseFloat(form.overtimePay) : null,
        bonus: form.bonus ? parseFloat(form.bonus) : null,
        tips: form.tips ? parseFloat(form.tips) : null,
        commission: form.commission ? parseFloat(form.commission) : null,
        taxDeduction: form.taxDeduction ? parseFloat(form.taxDeduction) : null,
        otherDeductions: form.otherDeductions ? parseFloat(form.otherDeductions) : null,
        notes: form.notes || null,
      });
      setCreateOpen(false);
      loadPayrolls();
    } catch (e) { console.error('Failed to create payroll:', e); alert(e.response?.data?.message || 'Failed'); }
  };

  const handleApprove = async (id) => {
    try {
      await financialAPI.approvePayroll(id, 'Admin');
      loadPayrolls();
    } catch (e) { console.error('Failed:', e); }
  };

  const handlePay = async () => {
    if (!selectedPayroll) return;
    try {
      await financialAPI.payPayroll(selectedPayroll.id, payForm.paymentDate, payForm.paymentMethod, payForm.transactionRef || null);
      setPayOpen(false);
      loadPayrolls();
    } catch (e) { console.error('Failed:', e); alert(e.response?.data?.message || 'Failed'); }
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Delete this payroll entry?')) return;
    try {
      await financialAPI.deletePayroll(id);
      loadPayrolls();
    } catch (e) { console.error('Failed:', e); }
  };

  const fmt = (n) => n != null ? Number(n).toLocaleString() : '—';

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Payroll</h1>
          <p className="text-muted-foreground">Manage employee salaries and payments</p>
        </div>
        <div className="flex gap-2">
          <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
            <SelectTrigger className="w-[200px]"><SelectValue /></SelectTrigger>
            <SelectContent>
              {restaurants.map(r => <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>)}
            </SelectContent>
          </Select>
        </div>
      </div>

      <Tabs defaultValue="salaries">
        <TabsList>
          <TabsTrigger value="salaries"><CalendarClock className="h-4 w-4 mr-2" /> Fixed Salaries</TabsTrigger>
          <TabsTrigger value="history"><DollarSign className="h-4 w-4 mr-2" /> Payment History</TabsTrigger>
        </TabsList>

        {/* Fixed Salaries Tab */}
        <TabsContent value="salaries" className="space-y-4">
          <div className="flex justify-end">
            <Button onClick={() => setSalaryOpen(true)}>
              <Plus className="h-4 w-4 mr-2" /> Add Employee Salary
            </Button>
          </div>
          <Card>
            <CardHeader>
              <CardTitle>Employee Fixed Salaries</CardTitle>
              <CardDescription>
                System auto-pays on the configured day each month
              </CardDescription>
            </CardHeader>
            <CardContent>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Employee</TableHead>
                    <TableHead className="text-right">Monthly Salary</TableHead>
                    <TableHead className="text-center">Pay Day</TableHead>
                    <TableHead className="text-center">Method</TableHead>
                    <TableHead className="text-center">Status</TableHead>
                    <TableHead>Last Paid</TableHead>
                    <TableHead className="text-right">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {salaryConfigs.length === 0 ? (
                    <TableRow><TableCell colSpan={7} className="text-center py-8 text-muted-foreground">No fixed salaries configured</TableCell></TableRow>
                  ) : (
                    salaryConfigs.map(sc => (
                      <TableRow key={sc.id}>
                        <TableCell className="font-medium">
                          {sc.employee?.firstName} {sc.employee?.lastName || sc.employee?.email}
                        </TableCell>
                        <TableCell className="text-right font-bold">{fmt(sc.monthlySalary)}</TableCell>
                        <TableCell className="text-center">{sc.payDay}th</TableCell>
                        <TableCell className="text-center"><Badge variant="outline">{sc.paymentMethod}</Badge></TableCell>
                        <TableCell className="text-center">
                          <Badge variant={sc.active ? 'default' : 'secondary'}>{sc.active ? 'Active' : 'Inactive'}</Badge>
                        </TableCell>
                        <TableCell className="text-sm text-muted-foreground">{sc.lastPaidDate || 'Never'}</TableCell>
                        <TableCell className="text-right">
                          <div className="flex gap-1 justify-end">
                            <Button variant="outline" size="sm" onClick={() => handlePayNow(sc.id)}>
                              <Play className="h-3 w-3 mr-1" /> Pay Now
                            </Button>
                            <Button variant="ghost" size="icon" onClick={() => handleDeleteSalary(sc.id)}>
                              <Trash2 className="h-4 w-4 text-red-500" />
                            </Button>
                          </div>
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </CardContent>
          </Card>
        </TabsContent>

        {/* Payment History Tab */}
        <TabsContent value="history" className="space-y-4">
          <div className="flex justify-end">
            <Button onClick={() => setCreateOpen(true)}>
              <Plus className="h-4 w-4 mr-2" /> Manual Payroll
            </Button>
          </div>

          {/* Summary */}
          <div className="grid gap-4 md:grid-cols-3">
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Total Entries</CardTitle>
                <DollarSign className="h-4 w-4 text-muted-foreground" />
              </CardHeader>
              <CardContent><div className="text-3xl font-bold">{payrolls.length}</div></CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Pending Payment</CardTitle>
                <CheckCircle className="h-4 w-4 text-amber-500" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold text-amber-600">
                  {payrolls.filter(p => p.status === 'PENDING' || p.status === 'APPROVED').length}
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
                <CardTitle className="text-sm font-medium">Total Paid</CardTitle>
                <CreditCard className="h-4 w-4 text-green-500" />
              </CardHeader>
              <CardContent>
                <div className="text-3xl font-bold text-green-600">
                  {fmt(payrolls.filter(p => p.status === 'PAID').reduce((sum, p) => sum + (p.netPay || 0), 0))}
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Payroll Table */}
          <Card>
        <CardHeader>
          <CardTitle>Payroll Entries</CardTitle>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Employee</TableHead>
                <TableHead>Period</TableHead>
                <TableHead>Type</TableHead>
                <TableHead className="text-right">Gross</TableHead>
                <TableHead className="text-right">Deductions</TableHead>
                <TableHead className="text-right">Net Pay</TableHead>
                <TableHead className="text-center">Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {payrolls.length === 0 ? (
                <TableRow><TableCell colSpan={8} className="text-center py-8 text-muted-foreground">No payroll entries</TableCell></TableRow>
              ) : (
                payrolls.map(p => (
                  <TableRow key={p.id}>
                    <TableCell className="font-medium">{p.employee?.fullName || p.employee?.email || `#${p.employee?.id}`}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">{p.payPeriodStart} — {p.payPeriodEnd}</TableCell>
                    <TableCell><Badge variant="outline">{p.payrollType}</Badge></TableCell>
                    <TableCell className="text-right">{fmt(p.grossPay)}</TableCell>
                    <TableCell className="text-right text-red-500">{fmt(p.totalDeductions)}</TableCell>
                    <TableCell className="text-right font-bold">{fmt(p.netPay)}</TableCell>
                    <TableCell className="text-center">
                      <Badge variant={STATUS_VARIANTS[p.status] || 'secondary'}>{p.status}</Badge>
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex gap-1 justify-end">
                        {p.status === 'PENDING' && (
                          <Button variant="outline" size="sm" onClick={() => handleApprove(p.id)}>Approve</Button>
                        )}
                        {p.status === 'APPROVED' && (
                          <Button variant="outline" size="sm" onClick={() => { setSelectedPayroll(p); setPayOpen(true); }}>
                            <CreditCard className="h-3 w-3 mr-1" /> Pay
                          </Button>
                        )}
                        {(p.status === 'PENDING' || p.status === 'APPROVED') && (
                          <Button variant="ghost" size="icon" onClick={() => handleDelete(p.id)}>
                            <Trash2 className="h-4 w-4 text-red-500" />
                          </Button>
                        )}
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </CardContent>
          </Card>
        </TabsContent>
      </Tabs>

      {/* Create Dialog */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="max-w-lg">
          <DialogHeader><DialogTitle>New Payroll Entry</DialogTitle></DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Employee *</Label>
                <select value={form.employeeId} onChange={e => setForm({ ...form, employeeId: e.target.value })} className="w-full border rounded-md px-3 py-2 text-sm bg-background">
                  <option value="">Select employee</option>
                  {employees.map(e => <option key={e.id} value={e.id}>{(e.fullName || '').trim() || e.email} ({e.role})</option>)}
                </select>
              </div>
              <div className="space-y-2">
                <Label>Payroll Type *</Label>
                <Select value={form.payrollType} onValueChange={v => setForm({ ...form, payrollType: v })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="HOURLY">Hourly</SelectItem>
                    <SelectItem value="SALARY">Salary</SelectItem>
                    <SelectItem value="CONTRACT">Contract</SelectItem>
                    <SelectItem value="COMMISSION">Commission</SelectItem>
                    <SelectItem value="ADVANCE">Advance Payment</SelectItem>
                  </SelectContent>
                </Select>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Period Start *</Label>
                <Input type="date" value={form.payPeriodStart} onChange={e => setForm({ ...form, payPeriodStart: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Period End *</Label>
                <Input type="date" value={form.payPeriodEnd} onChange={e => setForm({ ...form, payPeriodEnd: e.target.value })} />
              </div>
            </div>
            {form.payrollType === 'HOURLY' && (
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>Hours Worked</Label>
                  <Input type="number" step="0.5" value={form.hoursWorked} onChange={e => setForm({ ...form, hoursWorked: e.target.value })} />
                </div>
                <div className="space-y-2">
                  <Label>Hourly Rate</Label>
                  <Input type="number" value={form.hourlyRate} onChange={e => setForm({ ...form, hourlyRate: e.target.value })} />
                </div>
              </div>
            )}
            {(form.payrollType === 'SALARY' || form.payrollType === 'CONTRACT' || form.payrollType === 'ADVANCE') && (
              <div className="space-y-2">
                <Label>Base Salary</Label>
                <Input type="number" value={form.baseSalary} onChange={e => setForm({ ...form, baseSalary: e.target.value })} />
              </div>
            )}
            <div className="grid grid-cols-3 gap-4">
              <div className="space-y-2">
                <Label>Overtime Pay</Label>
                <Input type="number" value={form.overtimePay} onChange={e => setForm({ ...form, overtimePay: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Bonus</Label>
                <Input type="number" value={form.bonus} onChange={e => setForm({ ...form, bonus: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Tips</Label>
                <Input type="number" value={form.tips} onChange={e => setForm({ ...form, tips: e.target.value })} />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Tax Deduction</Label>
                <Input type="number" value={form.taxDeduction} onChange={e => setForm({ ...form, taxDeduction: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Other Deductions</Label>
                <Input type="number" value={form.otherDeductions} onChange={e => setForm({ ...form, otherDeductions: e.target.value })} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Notes</Label>
              <Input value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setCreateOpen(false)}>Cancel</Button>
            <Button onClick={handleCreate} disabled={!form.employeeId || !form.payPeriodStart || !form.payPeriodEnd}>Create</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Pay Dialog */}
      <Dialog open={payOpen} onOpenChange={setPayOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader><DialogTitle>Process Payment</DialogTitle></DialogHeader>
          {selectedPayroll && (
            <div className="py-2 space-y-4">
              <div className="text-center">
                <p className="text-sm text-muted-foreground">{selectedPayroll.employee?.fullName}</p>
                <p className="text-3xl font-bold">{fmt(selectedPayroll.netPay)}</p>
              </div>
              <div className="space-y-2">
                <Label>Payment Date</Label>
                <Input type="date" value={payForm.paymentDate} onChange={e => setPayForm({ ...payForm, paymentDate: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label>Payment Method</Label>
                <Select value={payForm.paymentMethod} onValueChange={v => setPayForm({ ...payForm, paymentMethod: v })}>
                  <SelectTrigger><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CASH">Cash</SelectItem>
                    <SelectItem value="BANK_TRANSFER">Bank Transfer</SelectItem>
                    <SelectItem value="CHECK">Check</SelectItem>
                    <SelectItem value="DIRECT_DEPOSIT">Direct Deposit</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>Transaction Ref</Label>
                <Input value={payForm.transactionRef} onChange={e => setPayForm({ ...payForm, transactionRef: e.target.value })} placeholder="Optional" />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setPayOpen(false)}>Cancel</Button>
            <Button onClick={handlePay}><CreditCard className="h-4 w-4 mr-2" /> Pay</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Salary Config Dialog */}
      <Dialog open={salaryOpen} onOpenChange={setSalaryOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>Add Fixed Salary</DialogTitle></DialogHeader>
          <div className="grid gap-4 py-2">
            <div className="space-y-2">
              <Label>Employee *</Label>
              <select value={salaryForm.employeeId} onChange={e => setSalaryForm({ ...salaryForm, employeeId: e.target.value })} className="w-full border rounded-md px-3 py-2 text-sm bg-background">
                <option value="">Select employee</option>
                {employees.map(e => <option key={e.id} value={e.id}>{(e.fullName || '').trim() || e.email} ({e.role})</option>)}
              </select>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Monthly Salary *</Label>
                <Input type="number" value={salaryForm.monthlySalary} onChange={e => setSalaryForm({ ...salaryForm, monthlySalary: e.target.value })} placeholder="e.g. 5000000" />
              </div>
              <div className="space-y-2">
                <Label>Pay Day (1-28) *</Label>
                <Input type="number" min="1" max="28" value={salaryForm.payDay} onChange={e => setSalaryForm({ ...salaryForm, payDay: e.target.value })} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Payment Method</Label>
              <Select value={salaryForm.paymentMethod} onValueChange={v => setSalaryForm({ ...salaryForm, paymentMethod: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="CASH">Cash</SelectItem>
                  <SelectItem value="BANK_TRANSFER">Bank Transfer</SelectItem>
                  <SelectItem value="CHECK">Check</SelectItem>
                  <SelectItem value="DIRECT_DEPOSIT">Direct Deposit</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>Notes</Label>
              <Input value={salaryForm.notes} onChange={e => setSalaryForm({ ...salaryForm, notes: e.target.value })} placeholder="Optional" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSalaryOpen(false)}>Cancel</Button>
            <Button onClick={handleCreateSalary} disabled={!salaryForm.employeeId || !salaryForm.monthlySalary || !salaryForm.payDay}>Save</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
