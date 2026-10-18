/**
 * @name umi 的路由配置
 * @description 只支持 path,component,routes,redirect,wrappers,name,icon 的配置
 * @param path  path 只支持两种占位符配置，第一种是动态参数 :id 的形式，第二种是 * 通配符，通配符只能出现路由字符串的最后。
 * @param component 配置 location 和 path 匹配后用于渲染的 React 组件路径。可以是绝对路径，也可以是相对路径，如果是相对路径，会从 src/pages 开始找起。
 * @param routes 配置子路由，通常在需要为多个路径增加 layout 组件时使用。
 * @param redirect 配置路由跳转
 * @param wrappers 配置路由组件的包装组件，通过包装组件可以为当前的路由组件组合进更多的功能。 比如，可以用于路由级别的权限校验
 * @param name 配置路由的标题，默认读取国际化文件 menu.ts 中 menu.xxxx 的值，如配置 name 为 login，则读取 menu.ts 中 menu.login 的取值作为标题
 * @param icon 配置路由的图标，取值参考 https://ant.design/components/icon-cn， 注意去除风格后缀和大小写，如想要配置图标为 <StepBackwardOutlined /> 则取值应为 stepBackward 或 StepBackward，如想要配置图标为 <UserOutlined /> 则取值应为 user 或者 User
 * @doc https://umijs.org/docs/guides/routes
 */
export default [
  {
    path: '/dash',
    icon: 'DashboardOutlined',
    name: 'dashboard',
    routes: [
      {
        name: 'day',
        path: '/dash/day',
        component: './Dashboard/Day',
      },
      {
        name: 'month',
        path: '/dash/month',
        component: './Dashboard/Month',
      },
    ],
  },
  {
    path: '/user',
    layout: false,
    routes: [
      {
        name: 'login',
        path: '/user/login',
        component: './User/Login',
      },
    ],
  },
  {
    path: '/customer',
    name: 'customer',
    icon: 'smile',
    routes: [
      {
        name: 'customer-create',
        path: '/customer/create',
        component: './Customer/Create',
      },
      {
        name: 'customer-query',
        path: '/customer/query',
        component: './Customer/Query',
      },
      {
        path: '/customer/update',
        component: './Customer/Update',
      },
    ],
  },
  {
    path: '/staff',
    name: 'staff',
    icon: 'user',
    routes: [
      {
        access: 'canAdmin',
        name: 'staff-create',
        path: '/staff/create',
        component: './Staff/Create',
      },
      {
        access: 'canAdmin',
        name: 'staff-query',
        path: '/staff/query',
        component: './Staff/Query',
      },
      {
        access: 'canAdmin',
        name: 'staff-shifts',
        path: '/staff/shifts',
        component: './Staff/Shift',
      },
      {
        path: '/staff/update',
        component: './Staff/Update',
      },
      {
        access: 'canUser',
        name: 'staff-sign',
        path: '/staff/sign',
        component: './Staff/Sign',
      },
      {
        access: 'canUser',
        name: 'staff-today-timesheet',
        path: '/staff/sign/timesheet/today',
        component: './Staff/Timesheet/Today',
      },
      {
        access: 'canUser',
        name: 'staff-leave',
        path: '/staff/leave',
        component: './Staff/Leave',
      },
      {
        name: 'timesheet-list',
        path: '/staff/timesheet/list',
        component: './Staff/Timesheet/List',
      },
      {
        access: 'canAdmin',
        name: 'timesheet-week',
        path: '/staff/timesheet/week',
        component: './Staff/Timesheet/Week',
      },
    ],
  },
  {
    path: '/',
    redirect: '/dash/day',
  },
  {
    path: '/appointment/',
    icon: 'CalendarOutlined',
    name: 'appointment',
    routes: [
      {
        name: 'appointment-schedule',
        path: '/appointment/schedule',
        component: './Appointment/Schedule',
      },
      {
        name: 'appointment-query',
        path: '/appointment/query',
        component: './Appointment/Query',
      },
      {
        name: 'appointment-room',
        path: '/appointment/room',
        component: './Appointment/Room',
      },
      {
        path: '/appointment/create',
        component: './Appointment/Create',
      },
    ],
  },
  {
    path: '/treat/',
    icon: 'table',
    name: 'treat',
    routes: [
      {
        path: '/treat/create',
        component: './Treat/Create',
      },
      {
        name: 'treat-query',
        path: '/treat/query',
        component: './Treat/Query',
      },
      {
        name: 'treat-acupoints',
        path: '/treat/acupoints',
        component: './Treat/Acupoint',
      },
      {
        path: '/treat/history',
        component: './Treat/History',
      },
    ],
  },
  {
    path: '/prescription/',
    icon: 'MedicineBoxOutlined',
    name: 'prescription',
    routes: [
      {
        name: 'prescription-create',
        path: '/prescription/create',
        component: './Prescription/Create',
      },
      {
        name: 'prescription-query',
        path: '/prescription/query',
        component: './Prescription/Query',
      },
      {
        name: 'prescription-dispensary',
        path: '/prescription/dispensary',
        component: './Prescription/Dispensary',
      },
      {
        name: 'prescription-templates',
        path: '/prescription/templates',
        component: './Prescription/Template',
      },
      {
        name: 'prescription-therapy-templates',
        path: '/prescription/therapy-templates',
        component: './Prescription/TherapyTemplate',
      },
      {
        name: 'prescription-formulas',
        path: '/prescription/formulas',
        component: './Prescription/Formula',
      },
      {
        name: 'prescription-herbs',
        path: '/prescription/herbs',
        component: './Herb/Query',
      },
      {
        name: 'herb-stock',
        path: '/prescription/herb-stock',
        component: './Herb/Stock',
      },
    ],
  },
  {
    path: '/item/',
    icon: 'FileDoneOutlined',
    name: 'item',
    routes: [
      {
        name: 'item-create',
        path: '/item/create',
        component: './Item/Create',
      },
      {
        name: 'item-query',
        path: '/item/query',
        component: './Item/Query',
      },
    ],
  },
  {
    path: '/order/',
    icon: 'AccountBookOutlined',
    name: 'order',
    routes: [
      {
        name: 'order-create',
        path: '/order/create',
        component: './Order/Create',
      },
      {
        path: '/order/query',
        component: './Order/Query',
      },
    ],
  },
  {
    path: '/billing/',
    icon: 'PayCircleOutlined',
    name: 'billing',
    routes: [
      {
        name: 'billing-settle',
        path: '/billing/settle',
        component: './Billing/Settle',
      },
      {
        name: 'billing-reconciliation',
        path: '/billing/reconciliation',
        component: './Billing/Reconciliation',
      },
    ],
  },
  {
    path: '/ads',
    icon: 'PlayCircleOutlined',
    name: 'ads',
    component: './Ads/Index',
  },
  {
    path: '/ads/materials',
    redirect: '/ads?tab=materials',
    hideInMenu: true,
  },
  {
    path: '/ads/schedules',
    redirect: '/ads?tab=schedules',
    hideInMenu: true,
  },
  {
    path: '/ads/screens',
    redirect: '/ads?tab=screens',
    hideInMenu: true,
  },
  {
    path: '/ads/calls',
    redirect: '/ads?tab=calls',
    hideInMenu: true,
  },
  {
    path: '/appearance',
    icon: 'BgColorsOutlined',
    name: 'appearance',
    component: './Appearance/Index',
  },
  {
    path: '/review/',
    icon: 'WhatsAppOutlined',
    name: 'review',
    routes: [
      {
        path: '/review/staff',
        name: 'staff',
        component: './Review/Staff',
      },
      {
        path: '/review/customer',
        name: 'customer',
        component: './Review/Customer',
      },
      {
        path: '/review/day',
        name: 'day',
        component: './Review/Day',
      },
      {
        path: '/review/plan',
        component: './Review/Plan',
      },
    ],
  },
  {
    path: '*',
    layout: false,
    component: './404',
  },
];
