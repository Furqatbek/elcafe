import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { supplierAPI } from '../../services/api';
import { useInventory } from '../../context/InventoryContext';
import InventoryLayout from './InventoryLayout';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../../components/ui/card';
import { Button } from '../../components/ui/button';
import { Badge } from '../../components/ui/badge';
import { Label } from '../../components/ui/label';
import { Input } from '../../components/ui/input';
import { Checkbox } from '../../components/ui/checkbox';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../../components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../../components/ui/table';
import {
  Plus,
  Edit,
  Trash2,
  Truck,
  Star,
  Phone,
  Mail,
  MapPin,
} from 'lucide-react';

export default function InventorySuppliers() {
  const { t } = useTranslation();
  const { selectedRestaurant, suppliers, loadSuppliers } = useInventory();

  const [supplierModalOpen, setSupplierModalOpen] = useState(false);
  const [editingSupplier, setEditingSupplier] = useState(null);
  const [supplierFormData, setSupplierFormData] = useState({
    name: '',
    code: '',
    contactPerson: '',
    phone: '',
    email: '',
    address: '',
    paymentTerms: '',
    creditLimit: '',
    currency: 'UZS',
    active: true,
    notes: '',
  });

  const handleAddSupplier = () => {
    setEditingSupplier(null);
    setSupplierFormData({
      name: '',
      code: '',
      contactPerson: '',
      phone: '',
      email: '',
      address: '',
      paymentTerms: '',
      creditLimit: '',
      currency: 'UZS',
      active: true,
      notes: '',
    });
    setSupplierModalOpen(true);
  };

  const handleEditSupplier = (supplier) => {
    setEditingSupplier(supplier);
    setSupplierFormData({
      name: supplier.name,
      code: supplier.code || '',
      contactPerson: supplier.contactPerson || '',
      phone: supplier.phone || '',
      email: supplier.email || '',
      address: supplier.address || '',
      paymentTerms: supplier.paymentTerms || '',
      creditLimit: supplier.creditLimit?.toString() || '',
      currency: supplier.currency || 'UZS',
      active: supplier.active,
      notes: supplier.notes || '',
    });
    setSupplierModalOpen(true);
  };

  const handleSaveSupplier = async () => {
    try {
      const data = {
        ...supplierFormData,
        restaurantId: selectedRestaurant,
        creditLimit: supplierFormData.creditLimit ? parseFloat(supplierFormData.creditLimit) : null,
      };

      if (editingSupplier) {
        await supplierAPI.update(editingSupplier.id, data);
      } else {
        await supplierAPI.create(data);
      }

      setSupplierModalOpen(false);
      loadSuppliers();
    } catch (error) {
      console.error('Failed to save supplier:', error);
      alert(t('inventory.suppliers.errors.saveFailed', 'Failed to save supplier'));
    }
  };

  const handleDeleteSupplier = async (id) => {
    if (!confirm(t('inventory.suppliers.confirmDelete', 'Are you sure you want to delete this supplier?'))) return;

    try {
      await supplierAPI.delete(id);
      loadSuppliers();
    } catch (error) {
      console.error('Failed to delete supplier:', error);
    }
  };

  const handleToggleSupplier = async (id) => {
    try {
      await supplierAPI.toggleActive(id);
      loadSuppliers();
    } catch (error) {
      console.error('Failed to toggle supplier:', error);
    }
  };

  // Stats
  const activeSuppliers = suppliers.filter(s => s.active).length;
  const preferredSuppliers = suppliers.filter(s => s.preferred).length;

  return (
    <InventoryLayout>
      <div className="space-y-6">
        {/* Stats */}
        <div className="grid gap-4 md:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.suppliers.stats.total', 'Total Suppliers')}</CardTitle>
              <Truck className="h-4 w-4 text-blue-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{suppliers.length}</div>
              <p className="text-xs text-muted-foreground">{activeSuppliers} {t('inventory.stats.active', 'active')}</p>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('inventory.suppliers.stats.preferred', 'Preferred')}</CardTitle>
              <Star className="h-4 w-4 text-yellow-600" />
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold">{preferredSuppliers}</div>
            </CardContent>
          </Card>
        </div>

        {/* Suppliers Table */}
        <Card>
          <CardHeader>
            <div className="flex items-center justify-between">
              <div>
                <CardTitle className="flex items-center gap-2">
                  <Truck className="h-5 w-5" />
                  {t('inventory.suppliers.title', 'Suppliers')}
                </CardTitle>
                <CardDescription>{t('inventory.suppliers.description', 'Manage your ingredient suppliers')}</CardDescription>
              </div>
              <Button onClick={handleAddSupplier}>
                <Plus className="h-4 w-4 mr-2" />
                {t('inventory.suppliers.addSupplier', 'Add Supplier')}
              </Button>
            </div>
          </CardHeader>
          <CardContent>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t('inventory.suppliers.fields.name', 'Name')}</TableHead>
                  <TableHead>{t('inventory.suppliers.fields.code', 'Code')}</TableHead>
                  <TableHead>{t('inventory.suppliers.fields.contact', 'Contact')}</TableHead>
                  <TableHead>{t('inventory.suppliers.fields.phone', 'Phone')}</TableHead>
                  <TableHead>{t('inventory.suppliers.fields.email', 'Email')}</TableHead>
                  <TableHead>{t('inventory.suppliers.fields.status', 'Status')}</TableHead>
                  <TableHead className="text-right">{t('common.actions')}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {suppliers.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                      {t('inventory.suppliers.noData', 'No suppliers found')}
                    </TableCell>
                  </TableRow>
                ) : (
                  suppliers.map((supplier) => (
                    <TableRow key={supplier.id}>
                      <TableCell className="font-medium">
                        <div className="flex items-center gap-2">
                          {supplier.name}
                          {supplier.preferred && <Star className="h-4 w-4 text-yellow-500 fill-yellow-500" />}
                        </div>
                      </TableCell>
                      <TableCell>{supplier.code || '-'}</TableCell>
                      <TableCell>{supplier.contactPerson || '-'}</TableCell>
                      <TableCell>
                        {supplier.phone && (
                          <div className="flex items-center gap-1">
                            <Phone className="h-3 w-3" />
                            {supplier.phone}
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        {supplier.email && (
                          <div className="flex items-center gap-1">
                            <Mail className="h-3 w-3" />
                            {supplier.email}
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        <Badge className={supplier.active ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}>
                          {supplier.active ? t('common.active', 'Active') : t('common.inactive', 'Inactive')}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="icon" onClick={() => handleEditSupplier(supplier)}>
                            <Edit className="h-4 w-4" />
                          </Button>
                          <Button variant="ghost" size="icon" onClick={() => handleDeleteSupplier(supplier.id)}>
                            <Trash2 className="h-4 w-4 text-red-600" />
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
      </div>

      {/* Add/Edit Modal */}
      <Dialog open={supplierModalOpen} onOpenChange={setSupplierModalOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>
              {editingSupplier ? t('inventory.suppliers.editSupplier', 'Edit Supplier') : t('inventory.suppliers.addSupplier', 'Add Supplier')}
            </DialogTitle>
          </DialogHeader>
          <div className="grid gap-4 py-4">
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('inventory.suppliers.fields.name', 'Name')} *</Label>
                <Input
                  value={supplierFormData.name}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, name: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.suppliers.fields.code', 'Code')}</Label>
                <Input
                  value={supplierFormData.code}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, code: e.target.value })}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('inventory.suppliers.fields.contact', 'Contact Person')}</Label>
                <Input
                  value={supplierFormData.contactPerson}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, contactPerson: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.suppliers.fields.phone', 'Phone')}</Label>
                <Input
                  value={supplierFormData.phone}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, phone: e.target.value })}
                />
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('inventory.suppliers.fields.email', 'Email')}</Label>
                <Input
                  type="email"
                  value={supplierFormData.email}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, email: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label>{t('inventory.suppliers.fields.creditLimit', 'Credit Limit')}</Label>
                <Input
                  type="number"
                  value={supplierFormData.creditLimit}
                  onChange={(e) => setSupplierFormData({ ...supplierFormData, creditLimit: e.target.value })}
                />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('inventory.suppliers.fields.address', 'Address')}</Label>
              <Input
                value={supplierFormData.address}
                onChange={(e) => setSupplierFormData({ ...supplierFormData, address: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>{t('inventory.suppliers.fields.paymentTerms', 'Payment Terms')}</Label>
              <Input
                value={supplierFormData.paymentTerms}
                onChange={(e) => setSupplierFormData({ ...supplierFormData, paymentTerms: e.target.value })}
                placeholder="Net 30"
              />
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                id="active"
                checked={supplierFormData.active}
                onCheckedChange={(checked) => setSupplierFormData({ ...supplierFormData, active: checked })}
              />
              <Label htmlFor="active">{t('inventory.suppliers.fields.active', 'Active')}</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setSupplierModalOpen(false)}>{t('common.cancel')}</Button>
            <Button onClick={handleSaveSupplier} disabled={!supplierFormData.name}>{t('common.save')}</Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </InventoryLayout>
  );
}
