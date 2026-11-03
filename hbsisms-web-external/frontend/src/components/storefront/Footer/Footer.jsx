import { Link } from 'react-router-dom'
import BrandLogo from '@/components/brand/BrandLogo'
import songThuyen from '@/assets/decorations/song-thuyen.png'
import hoaPhuong from '@/assets/decorations/hoa-phuong.png'
import songLine from '@/assets/decorations/song-line.png'
import facebookLogo from '@/assets/social/facebook.svg'
import zaloLogo from '@/assets/social/zalo.svg'
import { ROUTES } from '@/constants/routes'
import './Footer.css'

export default function Footer() {
  return (
    <footer className="site-footer">
      <img
        className="decoration site-footer__flowers"
        src={hoaPhuong}
        alt=""
        aria-hidden="true"
      />
      <img
        className="decoration site-footer__boat"
        src={songThuyen}
        alt=""
        aria-hidden="true"
      />

      <div className="container site-footer__grid">
        <div className="site-footer__brand">
          <BrandLogo tone="light" className="site-footer__logo" />
          <p>
            Hải sản sơ chế, đóng khay tiện lợi cho bữa ăn gia đình.
          </p>
          <p className="site-footer__place">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
              <path d="M12 21s7-5.5 7-12a7 7 0 1 0-14 0c0 6.5 7 12 7 12z" />
              <circle cx="12" cy="9" r="2.5" />
            </svg>
            Hải Phòng
          </p>
        </div>

        <div>
          <h3>Về Hòa Bình</h3>
          <ul>
            <li><Link to={ROUTES.ABOUT}>Giới thiệu cửa hàng</Link></li>
            <li><Link to={ROUTES.PRODUCTS}>Sản phẩm</Link></li>
            <li><Link to={ROUTES.CONTACT}>Liên hệ</Link></li>
          </ul>
        </div>

        <div>
          <h3>Hỗ trợ khách hàng</h3>
          <ul>
            <li><Link to={ROUTES.CONTACT}>Hướng dẫn mua hàng</Link></li>
            <li><Link to={ROUTES.CONTACT}>Chính sách giao hàng</Link></li>
            <li><Link to={ROUTES.CONTACT}>Chính sách đổi trả</Link></li>
            <li><Link to={ROUTES.CONTACT}>Chính sách bảo mật</Link></li>
          </ul>
        </div>

        <div className="site-footer__connect">
          <h3>Kết nối với Hòa Bình</h3>

          {/* TODO: thay "#" bằng link Facebook / Zalo thật của cửa hàng */}
          <div className="site-footer__socials">
            <a href="#" className="site-footer__social" aria-label="Facebook">
              <img src={facebookLogo} alt="" />
            </a>
            <a href="#" className="site-footer__social" aria-label="Zalo">
              <img src={zaloLogo} alt="" />
            </a>
          </div>

          <p className="site-footer__help-title">Bạn cần hỗ trợ?</p>
          <p className="site-footer__help-text">
            Liên hệ cửa hàng để được tư vấn sản phẩm.
          </p>

          <Link to={ROUTES.CONTACT} className="site-footer__cta">
            Liên hệ ngay
            <span className="site-footer__cta-arrow" aria-hidden="true">→</span>
          </Link>
        </div>
      </div>

      <div className="site-footer__bottom">
        <div className="container site-footer__bottom-inner">
          <p>© {new Date().getFullYear()} Hải sản Hòa Bình. Bảo lưu mọi quyền.</p>
          <div className="site-footer__tagline-wrap">
            <img
              className="site-footer__tagline-wave"
              src={songLine}
              alt=""
              aria-hidden="true"
            />
            <p className="site-footer__tagline">Đậm vị biển. Ấm bữa nhà.</p>
          </div>
        </div>
      </div>
    </footer>
  )
}
