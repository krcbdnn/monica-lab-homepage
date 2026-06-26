# ERD 초안

## Admin

* id
* username
* password
* name
* role
* created_at
* updated_at

---

## Notice

* id
* title
* content
* status
* view_count
* created_at
* updated_at
* admin_id

---

## ResearchMaterial

* id
* title
* content
* status
* view_count
* created_at
* updated_at
* admin_id

---

## News

* id
* title
* content
* status
* view_count
* created_at
* updated_at
* admin_id

---

## Inquiry

* id
* name
* email
* phone
* content
* status
* answer
* created_at
* updated_at

---

## Banner

* id
* title
* image_url
* display_order
* active
* created_at
* updated_at

---

## AttachmentFile

* id
* original_name
* stored_name
* file_path
* file_size
* created_at

---

## AdminLog

* id
* admin_id
* action_type
* target_type
* target_id
* created_at
