import './PlaceholderPage.css'

/** Trang khung sẵn cho các màn hình chưa thiết kế chi tiết */
export default function PlaceholderPage({ title, description, bullets = [] }) {
  return (
    <div className="placeholder-page">
      <h2>{title}</h2>
      <p>{description}</p>
      {bullets.length > 0 && (
        <ul>
          {bullets.map((item) => (
            <li key={item}>{item}</li>
          ))}
        </ul>
      )}
    </div>
  )
}
