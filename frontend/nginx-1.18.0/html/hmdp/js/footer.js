Vue.component("footBar", {
  template: `
    <aside class="desktop-nav">
      <div class="desktop-brand" @click="toPage(1)">
        <div class="desktop-brand-mark">H</div>
        <div>
          <div class="desktop-brand-name">HYEEE</div>
          <div class="desktop-brand-sub">Local Life</div>
        </div>
      </div>
      <nav class="desktop-nav-list">
        <button class="desktop-nav-item" :class="{active: activeBtn === 1}" @click="toPage(1)">
          <i class="el-icon-s-home"></i>
          <span>首页</span>
        </button>
        <button class="desktop-nav-item" :class="{active: activeBtn === 2}" @click="toPage(2)">
          <i class="el-icon-map-location"></i>
          <span>附近商铺</span>
        </button>
        <button class="desktop-nav-item" :class="{active: activeBtn === 0}" @click="toPage(0)">
          <i class="el-icon-edit-outline"></i>
          <span>发布探店</span>
        </button>
        <button class="desktop-nav-item" :class="{active: activeBtn === 3}" @click="toPage(3)">
          <i class="el-icon-chat-dot-round"></i>
          <span>AI 聊天</span>
        </button>
        <button class="desktop-nav-item" :class="{active: activeBtn === 4}" @click="toPage(4)">
          <i class="el-icon-user"></i>
          <span>个人中心</span>
        </button>
      </nav>
      <div class="desktop-nav-foot">Web Edition</div>
    </aside>
  `,
  props: ['activeBtn'],
  methods: {
    toPage(i) {
      if (i === 0) {
        location.href = "/blog-edit.html"
      } else if (i === 4) {
        location.href = "/info.html"
      } else if (i === 1) {
        location.href = "/"
      } else if (i === 2) {
        location.href = "/shop-list.html?type=1&name=美食"
      } else if (i === 3) {
        location.href = "/chat.html"
      }
    }
  }
})
