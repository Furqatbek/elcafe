import { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { reviewAPI, restaurantAPI } from '../services/api';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '../components/ui/card';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { Input } from '../components/ui/input';
import { Label } from '../components/ui/label';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '../components/ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table';
import {
  Star,
  MessageSquare,
  AlertTriangle,
  Eye,
  EyeOff,
  Reply,
  TrendingUp,
  QrCode,
  Download,
  Link,
} from 'lucide-react';

function StarDisplay({ rating, size = 'w-4 h-4' }) {
  return (
    <div className="flex gap-0.5">
      {[1, 2, 3, 4, 5].map((star) => (
        <Star
          key={star}
          className={`${size} ${star <= rating ? 'text-yellow-400 fill-yellow-400' : 'text-gray-300'}`}
        />
      ))}
    </div>
  );
}

export default function Reviews() {
  const { t } = useTranslation();
  const [restaurants, setRestaurants] = useState([]);
  const [selectedRestaurant, setSelectedRestaurant] = useState('');
  const [reviews, setReviews] = useState([]);
  const [summary, setSummary] = useState(null);
  const [filterRating, setFilterRating] = useState(null);
  const [replyOpen, setReplyOpen] = useState(false);
  const [replyReview, setReplyReview] = useState(null);
  const [replyText, setReplyText] = useState('');
  const [qrOpen, setQrOpen] = useState(false);

  useEffect(() => {
    restaurantAPI.getAll({ page: 0, size: 100 }).then(res => {
      const list = res.data.data?.content || res.data.data || [];
      setRestaurants(Array.isArray(list) ? list : []);
      if (list.length > 0) setSelectedRestaurant(list[0].id.toString());
    }).catch(console.error);
  }, []);

  useEffect(() => {
    if (!selectedRestaurant) return;
    loadReviews();
    loadSummary();
  }, [selectedRestaurant]);

  const loadReviews = async () => {
    try {
      const res = await reviewAPI.getAll(selectedRestaurant);
      setReviews(res.data.data || []);
    } catch (err) {
      console.error('Failed to load reviews:', err);
    }
  };

  const loadSummary = async () => {
    try {
      const res = await reviewAPI.getAdminSummary(selectedRestaurant);
      setSummary(res.data.data || null);
    } catch (err) {
      console.error('Failed to load summary:', err);
    }
  };

  const handleReply = async () => {
    if (!replyReview || !replyText.trim()) return;
    try {
      await reviewAPI.reply(replyReview.id, { reply: replyText, repliedBy: 'Admin' });
      setReplyOpen(false);
      setReplyText('');
      setReplyReview(null);
      loadReviews();
    } catch (err) {
      console.error('Failed to reply:', err);
    }
  };

  const handleHide = async (id) => {
    try {
      await reviewAPI.hide(id);
      loadReviews();
    } catch (err) {
      console.error('Failed to hide review:', err);
    }
  };

  const handlePublish = async (id) => {
    try {
      await reviewAPI.publish(id);
      loadReviews();
    } catch (err) {
      console.error('Failed to publish review:', err);
    }
  };

  const reviewUrl = selectedRestaurant
    ? `${window.location.origin}/admin/review?restaurant=${selectedRestaurant}`
    : '';

  const qrImageUrl = reviewUrl
    ? `https://api.qrserver.com/v1/create-qr-code/?size=400x400&data=${encodeURIComponent(reviewUrl)}`
    : '';

  const handleDownloadQR = () => {
    if (!qrImageUrl) return;
    const link = document.createElement('a');
    link.href = qrImageUrl;
    link.download = `review-qr-restaurant-${selectedRestaurant}.png`;
    link.click();
  };

  const handleCopyLink = () => {
    if (!reviewUrl) return;
    navigator.clipboard.writeText(reviewUrl);
  };

  const filteredReviews = filterRating
    ? reviews.filter(r => r.rating === filterRating)
    : reviews;

  const lowRatingCount = reviews.filter(r => r.lowRating).length;

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{t('review.title', 'Customer Reviews')}</h1>
          <p className="text-muted-foreground">{t('review.subtitle', 'Monitor customer feedback and respond to reviews')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" onClick={() => setQrOpen(true)} disabled={!selectedRestaurant}>
            <QrCode className="h-4 w-4 mr-2" />
            {t('review.qrCode', 'Review QR Code')}
          </Button>
          <Select value={selectedRestaurant} onValueChange={setSelectedRestaurant}>
            <SelectTrigger className="w-[200px]">
              <SelectValue placeholder={t('common.selectRestaurant', 'Select Restaurant')} />
            </SelectTrigger>
            <SelectContent>
              {restaurants.map(r => (
                <SelectItem key={r.id} value={r.id.toString()}>{r.name}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>

      {/* Summary Cards */}
      {summary && (
        <div className="grid gap-4 md:grid-cols-4">
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('review.averageRating', 'Average Rating')}</CardTitle>
              <Star className="h-4 w-4 text-yellow-400 fill-yellow-400" />
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold">{summary.averageRating}</div>
              <StarDisplay rating={Math.round(summary.averageRating)} />
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('review.totalReviews', 'Total Reviews')}</CardTitle>
              <MessageSquare className="h-4 w-4 text-muted-foreground" />
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold">{summary.totalReviews}</div>
            </CardContent>
          </Card>
          <Card>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('review.fiveStars', '5 Stars')}</CardTitle>
              <TrendingUp className="h-4 w-4 text-green-500" />
            </CardHeader>
            <CardContent>
              <div className="text-3xl font-bold text-green-600">{summary.fiveStars}</div>
              <p className="text-xs text-muted-foreground">
                {summary.totalReviews > 0 ? Math.round((summary.fiveStars / summary.totalReviews) * 100) : 0}%
              </p>
            </CardContent>
          </Card>
          <Card className={lowRatingCount > 0 ? 'border-red-300 bg-red-50' : ''}>
            <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-2">
              <CardTitle className="text-sm font-medium">{t('review.lowRatings', 'Low Ratings (1-2)')}</CardTitle>
              <AlertTriangle className={`h-4 w-4 ${lowRatingCount > 0 ? 'text-red-500' : 'text-muted-foreground'}`} />
            </CardHeader>
            <CardContent>
              <div className={`text-3xl font-bold ${lowRatingCount > 0 ? 'text-red-600' : ''}`}>
                {lowRatingCount}
              </div>
              {lowRatingCount > 0 && (
                <p className="text-xs text-red-500 font-medium">{t('review.needsAttention', 'Needs attention')}</p>
              )}
            </CardContent>
          </Card>
        </div>
      )}

      {/* Rating Distribution */}
      {summary && summary.totalReviews > 0 && (
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium">{t('review.ratingDistribution', 'Rating Distribution')}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="space-y-2">
              {[
                { stars: 5, count: summary.fiveStars },
                { stars: 4, count: summary.fourStars },
                { stars: 3, count: summary.threeStars },
                { stars: 2, count: summary.twoStars },
                { stars: 1, count: summary.oneStars },
              ].map(({ stars, count }) => (
                <div key={stars} className="flex items-center gap-3">
                  <span className="text-sm w-8 text-right">{stars}★</span>
                  <div className="flex-1 bg-gray-200 rounded-full h-3">
                    <div
                      className={`h-3 rounded-full ${stars >= 4 ? 'bg-green-500' : stars === 3 ? 'bg-yellow-500' : 'bg-red-500'}`}
                      style={{ width: `${summary.totalReviews > 0 ? (count / summary.totalReviews) * 100 : 0}%` }}
                    />
                  </div>
                  <span className="text-sm text-muted-foreground w-8">{count}</span>
                </div>
              ))}
            </div>
          </CardContent>
        </Card>
      )}

      {/* Reviews Table */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>{t('review.allReviews', 'All Reviews')}</CardTitle>
              <CardDescription>{t('review.allReviewsDesc', 'View and respond to customer feedback')}</CardDescription>
            </div>
            <div className="flex gap-1.5">
              <Button variant={filterRating === null ? 'default' : 'outline'} size="sm" onClick={() => setFilterRating(null)}>
                {t('common.all', 'All')}
              </Button>
              {[5, 4, 3, 2, 1].map(r => (
                <Button key={r} variant={filterRating === r ? 'default' : 'outline'} size="sm" onClick={() => setFilterRating(r)}>
                  {r}★
                </Button>
              ))}
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('review.rating', 'Rating')}</TableHead>
                <TableHead>{t('review.customer', 'Customer')}</TableHead>
                <TableHead>{t('review.order', 'Order')}</TableHead>
                <TableHead className="max-w-md">{t('review.comment', 'Comment')}</TableHead>
                <TableHead>{t('review.status', 'Status')}</TableHead>
                <TableHead>{t('review.date', 'Date')}</TableHead>
                <TableHead className="text-right">{t('common.actions', 'Actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filteredReviews.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} className="text-center py-8 text-muted-foreground">
                    {t('review.noReviews', 'No reviews yet')}
                  </TableCell>
                </TableRow>
              ) : (
                filteredReviews.map(review => (
                  <TableRow key={review.id} className={review.lowRating ? 'bg-red-50' : ''}>
                    <TableCell>
                      <div className="flex items-center gap-2">
                        <StarDisplay rating={review.rating} />
                        {review.lowRating && (
                          <AlertTriangle className="w-4 h-4 text-red-500" />
                        )}
                      </div>
                    </TableCell>
                    <TableCell className="font-medium">{review.customerName || '—'}</TableCell>
                    <TableCell className="text-muted-foreground">{review.orderNumber || '—'}</TableCell>
                    <TableCell className="max-w-md">
                      <p className="truncate">{review.comment || '—'}</p>
                      {review.reply && (
                        <p className="text-sm text-blue-600 mt-1 truncate">
                          ↳ {review.reply}
                        </p>
                      )}
                    </TableCell>
                    <TableCell>
                      <Badge variant={review.status === 'PUBLISHED' ? 'default' : 'secondary'}>
                        {review.status === 'PUBLISHED' ? t('review.published', 'Published') : t('review.hidden', 'Hidden')}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-muted-foreground whitespace-nowrap">
                      {new Date(review.createdAt).toLocaleDateString()}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex gap-1 justify-end">
                        <Button variant="ghost" size="icon" title={t('review.reply', 'Reply')}
                          onClick={() => { setReplyReview(review); setReplyText(review.reply || ''); setReplyOpen(true); }}>
                          <Reply className="h-4 w-4" />
                        </Button>
                        {review.status === 'PUBLISHED' ? (
                          <Button variant="ghost" size="icon" title={t('review.hide', 'Hide')} onClick={() => handleHide(review.id)}>
                            <EyeOff className="h-4 w-4 text-gray-500" />
                          </Button>
                        ) : (
                          <Button variant="ghost" size="icon" title={t('review.publish', 'Publish')} onClick={() => handlePublish(review.id)}>
                            <Eye className="h-4 w-4 text-green-600" />
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

      {/* QR Code Dialog */}
      <Dialog open={qrOpen} onOpenChange={setQrOpen}>
        <DialogContent className="max-w-sm">
          <DialogHeader>
            <DialogTitle>{t('review.qrCode', 'Review QR Code')}</DialogTitle>
            <DialogDescription>{t('review.qrCodeDesc', 'Customers scan this to leave a review')}</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col items-center gap-4 py-4">
            {qrImageUrl && (
              <img src={qrImageUrl} alt="Review QR Code" className="w-64 h-64 border rounded-lg" />
            )}
            <div className="w-full bg-muted rounded-lg p-3 text-sm text-center break-all text-muted-foreground">
              {reviewUrl}
            </div>
          </div>
          <DialogFooter className="flex gap-2 sm:gap-0">
            <Button variant="outline" onClick={handleCopyLink}>
              <Link className="h-4 w-4 mr-2" />
              {t('review.copyLink', 'Copy Link')}
            </Button>
            <Button onClick={handleDownloadQR}>
              <Download className="h-4 w-4 mr-2" />
              {t('review.downloadQR', 'Download QR')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reply Dialog */}
      <Dialog open={replyOpen} onOpenChange={setReplyOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{t('review.replyToReview', 'Reply to Review')}</DialogTitle>
            <DialogDescription>
              {replyReview && (
                <span className="flex items-center gap-2 mt-1">
                  <StarDisplay rating={replyReview.rating} />
                  <span>{replyReview.customerName || t('review.anonymous', 'Anonymous')}</span>
                </span>
              )}
            </DialogDescription>
          </DialogHeader>
          {replyReview?.comment && (
            <p className="text-sm bg-muted rounded-lg p-3 italic">"{replyReview.comment}"</p>
          )}
          <div className="space-y-2">
            <Label>{t('review.yourReply', 'Your Reply')}</Label>
            <textarea
              value={replyText}
              onChange={(e) => setReplyText(e.target.value)}
              rows={3}
              placeholder={t('review.replyPlaceholder', 'Thank you for your feedback...')}
              className="w-full border rounded-lg px-3 py-2 text-sm resize-none focus:ring-2 focus:ring-blue-500 outline-none"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setReplyOpen(false)}>{t('common.cancel', 'Cancel')}</Button>
            <Button onClick={handleReply} disabled={!replyText.trim()}>
              <Reply className="h-4 w-4 mr-2" /> {t('review.sendReply', 'Send Reply')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
