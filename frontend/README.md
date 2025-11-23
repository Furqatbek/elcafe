# Restaurant Delivery Control Panel - Frontend

Modern, minimalist frontend application built with React, Vite, and Shadcn UI for managing restaurant delivery operations.

## 🎨 Design Philosophy

- **Minimalist UI**: Clean, focused interface with essential features
- **Mobile Responsive**: Works seamlessly across all device sizes
- **Fast & Lightweight**: Built with Vite for optimal performance
- **Accessible**: Using Radix UI primitives for accessibility

## 🛠️ Tech Stack

| Technology | Purpose |
|-----------|---------|
| **React 18** | UI Library |
| **Vite** | Build Tool & Dev Server |
| **Shadcn UI** | Component Library |
| **Tailwind CSS** | Styling |
| **React Router** | Navigation |
| **Zustand** | State Management |
| **Axios** | API Client |
| **Lucide React** | Icons |

## 🚀 Quick Start

### Development Mode

```bash
cd frontend
npm install
npm run dev
```

The app will be available at http://localhost:3000

### Production Build

```bash
npm run build
npm run preview
```

### Docker

```bash
# Build frontend image
docker build -t elcafe-frontend .

# Run container
docker run -p 3000:80 elcafe-frontend
```

## 📁 Project Structure

```
frontend/
├── public/              # Static assets
├── src/
│   ├── components/      # Reusable components
│   │   ├── ui/         # Shadcn UI components
│   │   └── Layout.jsx  # Main layout wrapper
│   ├── pages/          # Page components
│   │   ├── Login.jsx
│   │   ├── Dashboard.jsx
│   │   ├── Orders.jsx
│   │   ├── Restaurants.jsx
│   │   └── Customers.jsx
│   ├── services/       # API services
│   │   └── api.js      # Axios configuration
│   ├── store/          # State management
│   │   └── authStore.js
│   ├── lib/            # Utilities
│   │   └── utils.js
│   ├── App.jsx         # Main app component
│   ├── main.jsx        # Entry point
│   └── index.css       # Global styles
├── Dockerfile          # Docker configuration
├── nginx.conf          # Nginx configuration
├── vite.config.js      # Vite configuration
└── tailwind.config.js  # Tailwind configuration
```

## 🎯 Features

### Authentication
- **Login**: JWT-based authentication
- **Auto Token Refresh**: Automatic token renewal
- **Protected Routes**: Secure navigation

### Dashboard
- **Overview Stats**: Real-time metrics
- **Quick Actions**: Common operations
- **Activity Feed**: Recent updates

### Orders Management
- **List View**: All orders with status
- **Status Updates**: One-click status progression
- **Filtering**: By status, date, restaurant
- **Real-time Updates**: Auto-refresh

### Restaurant Management
- **Grid View**: Visual restaurant cards
- **Quick Actions**: Edit, view menu
- **Status Indicators**: Active, accepting orders

### Customer CRM
- **Customer List**: All customer information
- **Order History**: Per-customer orders
- **Contact Info**: Email, phone, address

## 🔐 Authentication Flow

```javascript
// Login
const { login } = useAuthStore();
await login({ email, password });

// Auto-stored in localStorage:
// - access_token
// - refresh_token

// Auto-attached to all API requests
// Automatic refresh on 401 errors
```

## 🎨 UI Components

### Core Components (Shadcn UI)

```jsx
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
```

### Usage Examples

```jsx
// Button variants
<Button>Default</Button>
<Button variant="outline">Outline</Button>
<Button variant="ghost">Ghost</Button>
<Button variant="destructive">Delete</Button>

// Card layout
<Card>
  <CardHeader>
    <CardTitle>Title</CardTitle>
    <CardDescription>Description</CardDescription>
  </CardHeader>
  <CardContent>
    Content here
  </CardContent>
</Card>

// Input with label
<div>
  <Label htmlFor="email">Email</Label>
  <Input id="email" type="email" />
</div>
```

## 🔌 API Integration

### API Service

```javascript
// services/api.js
import { authAPI, orderAPI, restaurantAPI } from './services/api';

// Authentication
await authAPI.login(credentials);
await authAPI.register(data);

// Orders
await orderAPI.getAll({ page: 0, size: 20 });
await orderAPI.updateStatus(id, status, notes);

// Restaurants
await restaurantAPI.getAll();
await restaurantAPI.getById(id);
```

### Auto-Retry & Token Refresh

The API client automatically:
- Adds Bearer token to requests
- Refreshes expired tokens
- Redirects to login on auth failure

## 🎨 Styling

### Tailwind CSS

```jsx
// Utility classes
<div className="flex items-center justify-between p-4">
  <h1 className="text-2xl font-bold">Title</h1>
  <Button size="sm">Action</Button>
</div>

// Responsive design
<div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
  {items.map(item => <Card key={item.id}>...</Card>)}
</div>
```

### CSS Variables

Defined in `index.css`:
```css
:root {
  --background: 0 0% 100%;
  --foreground: 222.2 84% 4.9%;
  --primary: 222.2 47.4% 11.2%;
  /* ... */
}
```

## 🚢 Deployment

### Environment Variables

Create `.env` file:

```bash
VITE_API_URL=http://localhost:8080/api/v1
```

For production:

```bash
VITE_API_URL=https://api.yourdomain.com/api/v1
```

### Docker Deployment

The Dockerfile uses multi-stage build:

1. **Build Stage**: npm install & build
2. **Production Stage**: Nginx serving static files

```dockerfile
# Build
FROM node:20-alpine AS build
RUN npm ci && npm run build

# Serve
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
```

### Nginx Configuration

- Serves React app
- Proxies `/api` to backend
- Enables gzip compression
- SPA routing support

## 📱 Pages

### Login (`/login`)
- Email & password inputs
- Remember credentials
- Demo credentials shown
- Auto-redirect after login

### Dashboard (`/dashboard`)
- Stats cards (orders, restaurants, customers)
- Recent activity feed
- Quick action buttons

### Orders (`/orders`)
- Order list with pagination
- Status badges with colors
- Quick status update buttons
- Order details expansion

### Restaurants (`/restaurants`)
- Grid layout
- Restaurant cards
- Status indicators
- Quick actions (edit, view menu)

### Customers (`/customers`)
- Customer cards
- Contact information
- Order history link
- CRUD operations

## 🔒 Security

### Best Practices Implemented

- **JWT Storage**: localStorage (consider httpOnly cookies for production)
- **Auto Token Refresh**: Seamless user experience
- **Protected Routes**: Authentication required
- **API Error Handling**: Proper error boundaries
- **Input Validation**: Client-side validation

### Production Recommendations

1. **Use HTTPS**: Always in production
2. **CSP Headers**: Content Security Policy
3. **Rate Limiting**: On authentication endpoints
4. **Secure Cookies**: For token storage (httpOnly, secure)

## 🧪 Development

### Adding New Pages

1. Create page component in `src/pages/`
2. Add route in `App.jsx`
3. Add navigation link in `Layout.jsx`

```jsx
// 1. Create page
// src/pages/NewPage.jsx
export default function NewPage() {
  return <div>New Page</div>;
}

// 2. Add route
// App.jsx
<Route path="newpage" element={<NewPage />} />

// 3. Add nav link
// Layout.jsx
{ to: '/newpage', icon: Icon, label: 'New Page' }
```

### Adding API Endpoints

```javascript
// services/api.js
export const newAPI = {
  getAll: () => api.get('/new-endpoint'),
  getById: (id) => api.get(`/new-endpoint/${id}`),
  create: (data) => api.post('/new-endpoint', data),
};
```

## 🎨 Customization

### Theme Colors

Edit `tailwind.config.js`:

```javascript
theme: {
  extend: {
    colors: {
      primary: {
        DEFAULT: "hsl(var(--primary))",
        foreground: "hsl(var(--primary-foreground))",
      },
    },
  },
}
```

### Component Styling

Override in `index.css`:

```css
@layer base {
  .custom-button {
    @apply bg-blue-500 text-white px-4 py-2 rounded;
  }
}
```

## 📊 Performance

### Optimization Techniques Used

- **Code Splitting**: React.lazy for routes
- **Bundle Analysis**: Vite build analysis
- **Asset Optimization**: Image compression
- **Caching**: API response caching
- **Lazy Loading**: Images and components

### Build Optimization

```bash
# Analyze bundle
npm run build -- --analyze

# Production build
npm run build
```

## 🐛 Debugging

### Development Tools

```javascript
// Log API requests
console.log('API Request:', config);

// Debug state
const store = useAuthStore();
console.log('Auth State:', store);
```

### Common Issues

**API Connection Failed**
```bash
# Check backend is running
curl http://localhost:8080/actuator/health

# Check CORS configuration
# Verify VITE_API_URL in .env
```

**Authentication Errors**
```bash
# Clear localStorage
localStorage.clear();

# Check token in DevTools > Application > localStorage
```

## 📖 Additional Resources

- [React Documentation](https://react.dev/)
- [Vite Documentation](https://vitejs.dev/)
- [Shadcn UI](https://ui.shadcn.com/)
- [Tailwind CSS](https://tailwindcss.com/)
- [React Router](https://reactrouter.com/)

## 🤝 Contributing

1. Create feature branch
2. Make changes
3. Test thoroughly
4. Submit pull request

## 📝 License

Same as parent project (Apache 2.0)

---

**Version**: 1.0.0
**Last Updated**: 2025-01-23
**Built with**: ⚛️ React + ⚡ Vite + 🎨 Shadcn UI
